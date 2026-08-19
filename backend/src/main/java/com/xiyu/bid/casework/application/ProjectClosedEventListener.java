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
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.scoreparse.entity.ScoreItem;
import com.xiyu.bid.scoreparse.repository.ScoreItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
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
    private final DocumentTextExtractor docTextExtractor;
    private final CaseAiMatcher caseAiMatcher;
    private final CasePrecipitationNotifier notifier;
    private final ObsShareUrlSigner obsShareUrlSigner;
    private final RestTemplate restTemplate;

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
            if (scoreItemsForGate.isEmpty()) {
                log.info("No score items found for project: {}, skip AI case slice", projectId);
                notifier.notifySuccess(projectId, event.getProjectName(), 0,
                        System.currentTimeMillis() - startTime, project, event.getTriggerUserId());
                return;
            }

            List<CaseAiMatcher.AiMatchedSlice> matchedSlices = caseAiMatcher.extractSlicesWithAi(markdown, scoreItemsForGate);

            CasePrecipitationContext ctx = loadProjectContext(projectId);

            List<KnowledgeCase> candidates = buildCases(event, scoreItemsForGate, matchedSlices, ctx);
            List<KnowledgeCase> existing = knowledgeCaseRepository.findBySourceProjectId(projectId);
            java.util.Set<String> existingTitles = existing.stream()
                    .map(KnowledgeCase::getScoringPointTitle)
                    .filter(java.util.Objects::nonNull)
                    .collect(java.util.stream.Collectors.toSet());

            List<KnowledgeCase> casesToSave = candidates.stream()
                    .filter(c -> !existingTitles.contains(c.getScoringPointTitle()))
                    .toList();

            if (!casesToSave.isEmpty()) {
                knowledgeCaseRepository.saveAll(casesToSave);
                log.info("Successfully persisted {} new knowledge cases for project: {} ({} skipped as duplicate)",
                        casesToSave.size(), projectId, candidates.size() - casesToSave.size());
                notifier.notifySuccess(projectId, event.getProjectName(), casesToSave.size(),
                        System.currentTimeMillis() - startTime, ctx.projectOpt.orElse(null), event.getTriggerUserId());
            } else {
                log.info("No new cases to save for project: {} (total candidates: {}, existing: {})",
                        projectId, candidates.size(), existing.size());
                notifier.notifySuccess(projectId, event.getProjectName(), 0,
                        System.currentTimeMillis() - startTime, ctx.projectOpt.orElse(null), event.getTriggerUserId());
            }

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
        if (bidFile == null || bidFile.getFilePath() == null) {
            return fallback;
        }

        String filePath = bidFile.getFilePath();
        boolean isObs = filePath.startsWith(FileUrlPrefixes.OBS_DIRECT);

        // 读取标书字节：obs-direct: 是 OBS 云存储引用，需签名后经预签名 URL 下载；其余视为本地/挂载路径直接读。
        byte[] fileBytes;
        try {
            fileBytes = loadFileBytes(filePath);
        } catch (RuntimeException | java.io.IOException e) {
            // OBS 是真实业务数据源，读不到必须上报失败，禁止让 AI 对着占位文本虚假成功；
            // 本地路径则保留容错（文件可能已迁移），回退占位继续后续流程。
            if (isObs) {
                log.error("读取 OBS 标书失败，中止案例沉淀: {}", filePath, e);
                throw e instanceof RuntimeException
                        ? (RuntimeException) e
                        : new IllegalStateException("读取 OBS 标书失败: " + filePath, e);
            }
            log.error("读取本地标书失败，回退占位文本: {}", filePath, e);
            return fallback;
        }

        try {
            byte[] extractTarget = fileBytes == null ? new byte[0] : fileBytes;
            ExtractedDocument doc = docTextExtractor.extract(
                    bidFile.getFileName(), "application/octet-stream", extractTarget);
            if (doc != null && doc.text() != null) {
                return doc.text();
            }
        } catch (RuntimeException e) {
            log.error("Failed to extract markdown from bid file: {}", filePath, e);
        }
        return fallback;
    }

    byte[] loadFileBytes(String filePath) throws java.io.IOException {
        if (filePath.startsWith(FileUrlPrefixes.OBS_DIRECT)) {
            Optional<String> signedUrl = obsShareUrlSigner.trySign(filePath);
            if (signedUrl.isEmpty()) {
                throw new IllegalStateException("OBS 签名失败，无法下载标书: " + filePath);
            }
            // 必须传 URI 而非 String：getForObject(URI,..) 不经过 UriTemplateHandler，
            // 预签名 URL 的 % 不被二次编码 → OBS 验签不再 SignatureDoesNotMatch(403)
            byte[] bytes = restTemplate.getForObject(URI.create(signedUrl.get()), byte[].class);
            if (bytes == null || bytes.length == 0) {
                throw new IllegalStateException("OBS 标书下载结果为空: " + filePath);
            }
            return bytes;
        }
        return Files.readAllBytes(Paths.get(filePath));
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

    /**
     * 将 score_item 实体映射为案例沉淀快照。
     * <p>映射口径（已确认）：title/detail 取 {@code score_item.dim}/{@code score_item.detail}，
     * category 同样取 {@code dim}，由 {@link CaseAiMatcher#extractCategory} 按中文关键词归类为
     * 技术 / 商务 / 实施服务 / 资质业绩。
     */
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
                    .findFirst()
                    .orElse(null);
            if (scoreItem == null) {
                log.warn("AI 返回的 criteriaId={} 未匹配到任何 score_item（共 {} 个），丢弃该片段；若为 0 入库请检查 LLM id 回显 / 文档解析",
                        slice.getDraftId(), scoreItems.size());
                continue;
            }

            KnowledgeCase kCase = new KnowledgeCase();
            kCase.setSourceProjectId(projectId);
            kCase.setSourceProjectName(event.getProjectName());
            kCase.setScoringPointTitle(scoreItem.title());
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
