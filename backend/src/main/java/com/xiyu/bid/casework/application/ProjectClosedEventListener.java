package com.xiyu.bid.casework.application;

import com.xiyu.bid.casework.infrastructure.ArchiveFile;
import com.xiyu.bid.casework.infrastructure.ArchiveFileRepository;
import com.xiyu.bid.casework.infrastructure.KnowledgeCase;
import com.xiyu.bid.casework.infrastructure.KnowledgeCaseRepository;
import com.xiyu.bid.casework.infrastructure.ProjectArchive;
import com.xiyu.bid.casework.infrastructure.ProjectArchiveRepository;
import com.xiyu.bid.docinsight.application.DocumentTextExtractor;
import com.xiyu.bid.docinsight.application.ExtractedDocument;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.projectworkflow.entity.ProjectScoreDraft;
import com.xiyu.bid.projectworkflow.repository.ProjectScoreDraftRepository;
import com.xiyu.bid.bidresult.entity.BidResultFetchResult;
import com.xiyu.bid.bidresult.repository.BidResultFetchResultRepository;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.repository.TenderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class ProjectClosedEventListener {

    private final ProjectArchiveRepository archiveRepository;
    private final ArchiveFileRepository fileRepository;
    private final ProjectScoreDraftRepository scoreDraftRepository;
    private final KnowledgeCaseRepository knowledgeCaseRepository;
    private final ProjectRepository projectRepository;
    private final TenderRepository tenderRepository;
    private final BidResultFetchResultRepository bidResultFetchResultRepository;
    private final DocumentTextExtractor docTextExtractor;
    private final CaseAiMatcher caseAiMatcher;
    private final CasePrecipitationNotifier notifier;

    @Async
    @EventListener
    @Transactional
    public void onProjectClosed(ProjectClosedEvent event) {
        long startTime = System.currentTimeMillis();
        Long projectId = event.getProjectId();
        log.info("Received project closed event for project: {}", projectId);

        Optional<Project> projOpt = projectRepository.findById(projectId);
        Project project = projOpt.orElse(null);

        try {
            // 前置条件硬校验：标书文件 + 评分项。任一缺失则拒绝触发并报失败通知。
            Optional<ProjectArchive> archiveForGateOpt = archiveRepository.findByProjectId(projectId);
            boolean hasBidFile = false;
            if (archiveForGateOpt.isPresent()) {
                List<ArchiveFile> files = fileRepository.findByArchiveId(archiveForGateOpt.get().getId());
                hasBidFile = files.stream().anyMatch(f -> "BID".equals(f.getDocumentCategory()));
            }
            List<ProjectScoreDraft> draftsForGate = scoreDraftRepository
                    .findByProjectIdOrderByCategoryAscSourceTableIndexAscSourceRowIndexAsc(projectId);
            boolean hasScoreItems = !draftsForGate.isEmpty();

            if (!hasBidFile || !hasScoreItems) {
                List<String> missing = new ArrayList<>();
                if (!hasBidFile) missing.add("缺少标书文件");
                if (!hasScoreItems) missing.add("缺少评分项");
                String reason = "前置条件不满足：" + String.join("；", missing);
                log.warn("Skip case precipitation for project {}: {}", projectId, reason);
                notifier.notifyFailure(projectId, event.getProjectName(), reason, project);
                return;
            }

            String markdown = loadBidFileMarkdown(event, projectId);
            if (draftsForGate.isEmpty()) {
                log.info("No score drafts found for project: {}, skip AI case slice", projectId);
                notifier.notifySuccess(projectId, event.getProjectName(), 0,
                        System.currentTimeMillis() - startTime, project);
                return;
            }

            List<CaseAiMatcher.AiMatchedSlice> matchedSlices = caseAiMatcher.extractSlicesWithAi(markdown, draftsForGate);

            CasePrecipitationContext ctx = loadProjectContext(projectId);

            List<KnowledgeCase> casesToSave = buildCases(event, draftsForGate, matchedSlices, ctx);

            if (!casesToSave.isEmpty()) {
                knowledgeCaseRepository.saveAll(casesToSave);
                log.info("Successfully persisted {} knowledge cases for project: {}", casesToSave.size(), projectId);
                notifier.notifySuccess(projectId, event.getProjectName(), casesToSave.size(),
                        System.currentTimeMillis() - startTime, ctx.projectOpt.orElse(null));
            } else {
                log.info("No cases to save for project: {}", projectId);
                notifier.notifySuccess(projectId, event.getProjectName(), 0,
                        System.currentTimeMillis() - startTime, ctx.projectOpt.orElse(null));
            }

        } catch (RuntimeException e) {
            log.error("Failed to process project closed AI slicing", e);
            String reason = e.getMessage() != null ? e.getMessage() : "处理异常，请检查系统日志";
            try {
                notifier.notifyFailure(projectId, event.getProjectName(), reason, project);
            } catch (RuntimeException notifyEx) {
                log.error("Failed to send failure notification for project {}", projectId, notifyEx);
            }
        }
    }

    private String loadBidFileMarkdown(ProjectClosedEvent event, Long projectId) {
        Optional<ProjectArchive> archiveOpt = archiveRepository.findByProjectId(projectId);
        if (archiveOpt.isEmpty()) {
            log.warn("No archive found for closed project: {}", projectId);
            return null;
        }

        ProjectArchive archive = archiveOpt.get();
        List<ArchiveFile> files = fileRepository.findByArchiveId(archive.getId());
        ArchiveFile bidFile = files.stream()
                .filter(f -> "BID".equals(f.getDocumentCategory()))
                .findFirst()
                .orElse(null);

        String fallback = "# " + event.getProjectName() + "\n暂无标书正文。";
        if (bidFile == null || bidFile.getFilePath() == null) {
            return fallback;
        }

        try {
            byte[] fileBytes = Files.readAllBytes(Paths.get(bidFile.getFilePath()));
            ExtractedDocument doc = docTextExtractor.extract(
                    bidFile.getFileName(), "application/octet-stream", fileBytes);
            if (doc != null && doc.text() != null) {
                return doc.text();
            }
        } catch (RuntimeException | java.io.IOException e) {
            log.error("Failed to extract markdown from bid file: {}", bidFile.getFilePath(), e);
        }
        return fallback;
    }

    private CasePrecipitationContext loadProjectContext(Long projectId) {
        // 蓝图 4.1.1.2.1 业务字段：行业 / 客户类型 / 产品线 — 三个独立语义。
        // 行业：tender.industry（带 project.industry 兜底）
        // 客户类型：project.customerType
        // 产品线：project.sourceModule（项目源模块，最接近"产品线"语义的字段；空时使用"综合产品线"）
        // 修复前走弯路：行业和产品线都从 tender.projectType 取，导致前端"按行业筛选"得到错位结果。
        String projectType = "综合";
        String customerType = "其他";
        String productLine = "综合产品线";
        String bidResult = null;
        Optional<Project> projectOpt = projectRepository.findById(projectId);

        if (projectOpt.isPresent()) {
            Project proj = projectOpt.get();
            if (proj.getCustomerType() != null && !proj.getCustomerType().isBlank()) {
                customerType = proj.getCustomerType();
            }
            // 行业：先取 tender.industry，再取 project.industry，最后默认"综合"
            Optional<Tender> tenderOpt = tenderRepository.findById(proj.getTenderId());
            if (tenderOpt.isPresent() && tenderOpt.get().getIndustry() != null
                    && !tenderOpt.get().getIndustry().isBlank()) {
                projectType = tenderOpt.get().getIndustry();
            } else if (proj.getIndustry() != null && !proj.getIndustry().isBlank()) {
                projectType = proj.getIndustry();
            }
            // 产品线：project.sourceModule（项目源模块），空时保持默认
            if (proj.getSourceModule() != null && !proj.getSourceModule().isBlank()) {
                productLine = proj.getSourceModule();
            }
            Optional<BidResultFetchResult> bidResultOpt = bidResultFetchResultRepository
                    .findFirstByProjectIdAndStatusOrderByConfirmedAtDescFetchTimeDesc(
                            projectId, BidResultFetchResult.Status.CONFIRMED);
            if (bidResultOpt.isPresent()) {
                bidResult = bidResultOpt.get().getResult().name();
            }
        }

        return new CasePrecipitationContext(projectOpt, projectType, customerType, productLine, bidResult);
    }

    private List<KnowledgeCase> buildCases(ProjectClosedEvent event, List<ProjectScoreDraft> drafts,
                                            List<CaseAiMatcher.AiMatchedSlice> matchedSlices,
                                            CasePrecipitationContext ctx) {
        Long projectId = event.getProjectId();
        List<KnowledgeCase> casesToSave = new ArrayList<>();

        for (CaseAiMatcher.AiMatchedSlice slice : matchedSlices) {
            ProjectScoreDraft draft = drafts.stream()
                    .filter(d -> d.getId().equals(slice.getDraftId()))
                    .findFirst()
                    .orElse(null);
            if (draft == null) continue;

            KnowledgeCase kCase = new KnowledgeCase();
            kCase.setSourceProjectId(projectId);
            kCase.setSourceProjectName(event.getProjectName());
            kCase.setScoringPointTitle(draft.getScoreItemTitle());
            kCase.setRequirementRaw(draft.getScoreRuleText() != null ? draft.getScoreRuleText() : "暂无要求");
            kCase.setResponseText(slice.getMatchedSnippet());
            kCase.setReuseCount(0);
            kCase.setStatus("ACTIVE");
            kCase.setCustomerType(ctx.customerType != null ? ctx.customerType : "国有企业");
            kCase.setProjectType(ctx.projectType != null ? ctx.projectType : "综合");
            kCase.setBidResult(ctx.bidResult);
            kCase.setScoringCategory(caseAiMatcher.extractCategory(draft.getCategory()));
            kCase.setProductLine(ctx.productLine);
            casesToSave.add(kCase);
        }
        return casesToSave;
    }

    private record CasePrecipitationContext(
            Optional<Project> projectOpt,
            String projectType,
            String customerType,
            String productLine,
            String bidResult) {
    }
}
