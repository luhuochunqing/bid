package com.xiyu.bid.casework.application;

import com.xiyu.bid.casework.infrastructure.ArchiveFile;
import com.xiyu.bid.casework.infrastructure.ArchiveFileRepository;
import com.xiyu.bid.casework.infrastructure.KnowledgeCase;
import com.xiyu.bid.casework.infrastructure.KnowledgeCaseRepository;
import com.xiyu.bid.casework.infrastructure.ProjectArchive;
import com.xiyu.bid.casework.infrastructure.ProjectArchiveRepository;
import com.xiyu.bid.docinsight.application.DocumentTextExtractor;
import com.xiyu.bid.docinsight.application.ExtractedDocument;
import com.xiyu.bid.file.application.ObsShareUrlSigner;
import com.xiyu.bid.file.domain.FileUrlPrefixes;
import com.xiyu.bid.bidresult.entity.BidResultFetchResult;
import com.xiyu.bid.bidresult.repository.BidResultFetchResultRepository;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.project.entity.ProjectResult;
import com.xiyu.bid.project.repository.ProjectResultRepository;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.scoreparse.entity.ScoreItem;
import com.xiyu.bid.scoreparse.repository.ScoreItemRepository;
import com.xiyu.bid.scoreparse.application.LoadedBidFile;
import com.xiyu.bid.scoreparse.application.SidecarStreamTextExtractor;
import com.xiyu.bid.scoreparse.application.StreamingBidFileFetcher;
import com.xiyu.bid.scoreparse.application.TempBidFileStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
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
    private final ScoreItemRepository scoreItemRepository;
    private final KnowledgeCaseRepository knowledgeCaseRepository;
    private final ProjectRepository projectRepository;
    private final TenderRepository tenderRepository;
    private final BidResultFetchResultRepository bidResultFetchResultRepository;
    private final ProjectResultRepository projectResultRepository;
    private final DocumentTextExtractor docTextExtractor;
    private final CaseAiMatcher caseAiMatcher;
    private final CasePrecipitationNotifier notifier;
    private final ObsShareUrlSigner obsShareUrlSigner;
    private final StreamingBidFileFetcher streamingFetcher;
    private final SidecarStreamTextExtractor streamTextExtractor;
    private final TempBidFileStore tempBidFileStore;
    private final TransactionTemplate transactionTemplate;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
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
            List<ScoreItemSnapshot> scoreItemsForGate = loadScoreItemSnapshots(projectId);
            boolean hasScoreItems = !scoreItemsForGate.isEmpty();

            if (!hasBidFile || !hasScoreItems) {
                List<String> missing = new ArrayList<>();
                if (!hasBidFile) missing.add("缺少标书文件");
                if (!hasScoreItems) missing.add("缺少评分项");
                String reason = "前置条件不满足：" + String.join("；", missing);
                log.warn("Skip case precipitation for project {}: {}", projectId, reason);
                notifier.notifyFailure(projectId, event.getProjectName(), reason, project, event.getTriggerUserId());
                return;
            }

            String markdown = loadBidFileMarkdown(event, projectId);

            List<CaseAiMatcher.AiMatchedSlice> matchedSlices = caseAiMatcher.extractSlicesWithAi(markdown, scoreItemsForGate);

            CasePrecipitationContext ctx = loadProjectContext(projectId);

            List<KnowledgeCase> candidates = buildCases(event, scoreItemsForGate, matchedSlices, ctx);
            List<KnowledgeCase> existing = knowledgeCaseRepository.findBySourceProjectId(projectId);
            // 幂等去重按评分项 ID（非标题）：同一项目可存在多个同标题评分项（如 61/62 均叫"投标报价"），
            // 按标题去重会误跳过合法评分项，唯一键与之一致（uk_source_proj_scoring_point，spec 049 验收发现）。
            java.util.Set<Long> existingPointIds = existing.stream()
                    .map(KnowledgeCase::getScoringPointId)
                    .filter(java.util.Objects::nonNull)
                    .collect(java.util.stream.Collectors.toSet());

            List<KnowledgeCase> casesToSave = candidates.stream()
                    .filter(c -> c.getScoringPointId() == null || !existingPointIds.contains(c.getScoringPointId()))
                    .toList();

            if (!casesToSave.isEmpty()) {
                // 事务只包落库：下载/转换/LLM 摘录等长耗时步骤都在事务外，避免长时间持有 DB 连接
                transactionTemplate.executeWithoutResult(status ->
                        knowledgeCaseRepository.saveAll(casesToSave));
                log.info("Successfully persisted {} new knowledge cases for project: {} ({} skipped as duplicate)",
                        casesToSave.size(), projectId, candidates.size() - casesToSave.size());
            }
            notifier.notifySuccess(projectId, event.getProjectName(), casesToSave.size(),
                    System.currentTimeMillis() - startTime, ctx.projectOpt.orElse(null), event.getTriggerUserId());

        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            log.info("Knowledge cases for project {} already persisted by concurrent event, treated as idempotent success", projectId);
            notifier.notifySuccess(projectId, event.getProjectName(), 0,
                    System.currentTimeMillis() - startTime, project, event.getTriggerUserId());
        } catch (RuntimeException e) {
            log.error("Failed to process project closed AI slicing", e);
            String reason = e.getMessage() != null ? e.getMessage() : "处理异常，请检查系统日志";
            try {
                notifier.notifyFailure(projectId, event.getProjectName(), reason, project, event.getTriggerUserId());
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
        String filePath = bidFile == null ? null : bidFile.getFilePath();
        if (filePath == null) {
            return fallback;
        }
        // taskId 绑定临时文件命名：OBS 流式加载可能 spill 落盘，结束必须 release 归还配额
        String taskId = "case-" + projectId;
        try {
            if (filePath.startsWith(FileUrlPrefixes.OBS_DIRECT)) {
                return loadObsBidFileMarkdown(filePath, bidFile, taskId);
            }
            return extractLocalBidFileMarkdown(filePath, bidFile, fallback);
        } finally {
            // InMemory 快路径 release 为幂等 no-op；OnDisk 删除 {taskId}.part
            tempBidFileStore.release(taskId);
        }
    }

    /** OBS 标书流式加载 + 二态分派（spec 049 T004）：≤50MB InMemory→docTextExtractor 快路径；
     * 超限 OnDisk→sidecar /convert-stream 流式转换。失败一律 rethrow，禁止占位文本虚假成功。 */
    private String loadObsBidFileMarkdown(String filePath, ArchiveFile bidFile, String taskId) {
        Optional<String> signedUrl = obsShareUrlSigner.trySign(filePath);
        if (signedUrl.isEmpty()) {
            throw new IllegalStateException("OBS 签名失败，无法下载标书: " + filePath);
        }
        LoadedBidFile loaded;
        try {
            loaded = streamingFetcher.fetch(signedUrl.get(), taskId, bidFile.getFileName(), "application/octet-stream");
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException("流式下载 OBS 标书失败: " + filePath, e);
        }
        switch (loaded) {
            case LoadedBidFile.InMemory mem -> {
                ExtractedDocument doc = docTextExtractor.extract(mem.fileName(), mem.contentType(), mem.content());
                return doc != null && doc.text() != null ? doc.text() : "";
            }
            case LoadedBidFile.OnDisk disk -> {
                try {
                    return streamTextExtractor.extract(disk.tempFile(), disk.fileName());
                } catch (IOException | InterruptedException e) {
                    if (e instanceof InterruptedException) {
                        Thread.currentThread().interrupt();
                    }
                    String detail = e.getMessage() == null || e.getMessage().isBlank()
                            ? e.getClass().getSimpleName()
                            : e.getMessage();
                    throw new IllegalStateException("流式提取 OBS 标书文本失败: " + filePath + "，原因：" + detail, e);
                }
            }
        }
    }

    private String extractLocalBidFileMarkdown(String filePath, ArchiveFile bidFile, String fallback) {
        try {
            byte[] fileBytes = Files.readAllBytes(Paths.get(filePath));
            ExtractedDocument doc = docTextExtractor.extract(
                    bidFile.getFileName(), "application/octet-stream", fileBytes);
            return doc != null && doc.text() != null ? doc.text() : fallback;
        } catch (RuntimeException | IOException e) {
            // 本地路径保留容错（文件可能已迁移）回退占位。
            log.error("读取本地标书失败，回退占位文本: {}", filePath, e);
            return fallback;
        }
    }

    private CasePrecipitationContext loadProjectContext(Long projectId) {
        // 蓝图 4.1.1.2.1 三独立语义：行业←tender.industry(兜底 project.industry)、客户类型←customerType、产品线←sourceModule（勿走 projectType，历史错位根因）
        String projectType = "综合", customerType = "其他", productLine = "综合产品线", bidResult = null;
        Optional<Project> projectOpt = projectRepository.findById(projectId);
        if (projectOpt.isPresent()) {
            Project proj = projectOpt.get();
            if (proj.getCustomerType() != null && !proj.getCustomerType().isBlank()) customerType = proj.getCustomerType();
            Optional<Tender> tenderOpt = tenderRepository.findById(proj.getTenderId());
            if (tenderOpt.isPresent() && tenderOpt.get().getIndustry() != null && !tenderOpt.get().getIndustry().isBlank()) {
                projectType = tenderOpt.get().getIndustry();
            } else if (proj.getIndustry() != null && !proj.getIndustry().isBlank()) {
                projectType = proj.getIndustry();
            }
            if (proj.getSourceModule() != null && !proj.getSourceModule().isBlank()) productLine = proj.getSourceModule();
            Optional<ProjectResult> projectResultOpt = projectResultRepository.findByProjectId(projectId);
            if (projectResultOpt.isPresent() && projectResultOpt.get().getResultType() != null) {
                bidResult = projectResultOpt.get().getResultType();
            } else {
                Optional<BidResultFetchResult> bidResultOpt = bidResultFetchResultRepository
                        .findFirstByProjectIdAndStatusOrderByConfirmedAtDescFetchTimeDesc(projectId, BidResultFetchResult.Status.CONFIRMED);
                if (bidResultOpt.isPresent()) bidResult = bidResultOpt.get().getResult().name();
            }
        }
        return new CasePrecipitationContext(projectOpt, projectType, customerType, productLine, bidResult);
    }

    /** 将 score_item 映射为案例沉淀快照：title/detail/category 均取 dim（口径已确认，category 由 extractCategory 归类）。 */
    private List<ScoreItemSnapshot> loadScoreItemSnapshots(Long projectId) {
        return scoreItemRepository.findByProjectIdOrderByItemIndexAsc(projectId).stream()
                .map(item -> new ScoreItemSnapshot(item.getId(), item.getDim(), item.getDetail(), item.getDim()))
                .toList();
    }

    private List<KnowledgeCase> buildCases(ProjectClosedEvent event, List<ScoreItemSnapshot> scoreItems,
                                            List<CaseAiMatcher.AiMatchedSlice> matchedSlices,
                                            CasePrecipitationContext ctx) {
        Long projectId = event.getProjectId();
        List<KnowledgeCase> casesToSave = new ArrayList<>();
        for (CaseAiMatcher.AiMatchedSlice slice : matchedSlices) {
            ScoreItemSnapshot scoreItem = scoreItems.stream()
                    .filter(s -> s.id().equals(slice.getDraftId()))
                    .findFirst().orElse(null);
            if (scoreItem == null) {
                log.warn("AI 返回的 criteriaId={} 未匹配到任何 score_item（共 {} 个），丢弃该片段；若为 0 入库请检查 LLM id 回显 / 文档解析",
                        slice.getDraftId(), scoreItems.size());
                continue;
            }
            KnowledgeCase kCase = new KnowledgeCase();
            kCase.setSourceProjectId(projectId);
            kCase.setSourceProjectName(event.getProjectName());
            kCase.setScoringPointTitle(scoreItem.title());
            kCase.setScoringPointId(scoreItem.id());
            kCase.setRequirementRaw(scoreItem.ruleText() != null ? scoreItem.ruleText() : "暂无要求");
            kCase.setResponseText(slice.getMatchedSnippet());
            kCase.setReuseCount(0);
            kCase.setStatus("ACTIVE");
            kCase.setCustomerType(ctx.customerType != null ? ctx.customerType : "国有企业");
            kCase.setProjectType(ctx.projectType != null ? ctx.projectType : "综合");
            kCase.setBidResult(ctx.bidResult);
            kCase.setScoringCategory(caseAiMatcher.extractCategory(scoreItem.category()));
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
