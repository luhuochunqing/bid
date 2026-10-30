package com.xiyu.bid.casework.application;

import com.xiyu.bid.casework.infrastructure.ArchiveFileRepository;
import com.xiyu.bid.casework.infrastructure.ArchiveFile;
import com.xiyu.bid.casework.infrastructure.KnowledgeCase;
import com.xiyu.bid.casework.infrastructure.KnowledgeCaseRepository;
import com.xiyu.bid.casework.infrastructure.ProjectArchive;
import com.xiyu.bid.casework.infrastructure.ProjectArchiveRepository;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
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
    private final CaseAiMatcher caseAiMatcher;
    private final CasePrecipitationNotifier notifier;
    private final TransactionTemplate transactionTemplate;
    private final BidDocumentLoader bidDocumentLoader;
    private final CasePrecipitationTaskStateService taskStateService;

    @Autowired
    public ProjectClosedEventListener(
            ProjectArchiveRepository archiveRepository,
            ArchiveFileRepository fileRepository,
            ScoreItemRepository scoreItemRepository,
            KnowledgeCaseRepository knowledgeCaseRepository,
            ProjectRepository projectRepository,
            TenderRepository tenderRepository,
            BidResultFetchResultRepository bidResultFetchResultRepository,
            ProjectResultRepository projectResultRepository,
            CaseAiMatcher caseAiMatcher,
            CasePrecipitationNotifier notifier,
            TransactionTemplate transactionTemplate,
            BidDocumentLoader bidDocumentLoader,
            CasePrecipitationTaskStateService taskStateService) {
        this.archiveRepository = archiveRepository;
        this.fileRepository = fileRepository;
        this.scoreItemRepository = scoreItemRepository;
        this.knowledgeCaseRepository = knowledgeCaseRepository;
        this.projectRepository = projectRepository;
        this.tenderRepository = tenderRepository;
        this.bidResultFetchResultRepository = bidResultFetchResultRepository;
        this.projectResultRepository = projectResultRepository;
        this.caseAiMatcher = caseAiMatcher;
        this.notifier = notifier;
        this.transactionTemplate = transactionTemplate;
        this.bidDocumentLoader = bidDocumentLoader;
        this.taskStateService = taskStateService;
    }

    @Async("scoreParseExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onProjectClosed(ProjectClosedEvent event) {
        long startTime = System.currentTimeMillis();
        Long projectId = event.getProjectId();
        String taskId = event.getTaskId();
        if (taskId == null) {
            var claim = taskStateService.claim(projectId, event.getTriggerUserId());
            if (claim.reused()) {
                log.info("Skip duplicate automatic case precipitation event; active task={} project={}",
                        claim.task().taskId(), projectId);
                return;
            }
            taskId = claim.task().taskId();
        }
        if (taskId != null && !taskStateService.start(taskId)) {
            log.info("Skip non-active case precipitation task {}", taskId);
            return;
        }
        final String executionTaskId = taskId;
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
                taskStateService.fail(executionTaskId, reason);
                notifier.notifyFailure(projectId, event.getProjectName(), reason, project, event.getTriggerUserId());
                return;
            }

            BidDocumentLoader.BidDocument bidDoc =
                    bidDocumentLoader.loadBidDocument(event.getProjectName(), projectId, executionTaskId);
            String markdown = bidDoc != null ? bidDoc.markdown() : null;

            List<CaseAiMatcher.AiMatchedSlice> matchedSlices = caseAiMatcher.extractSlicesWithAi(markdown, scoreItemsForGate);
            CasePrecipitationContext ctx = loadProjectContext(projectId);

            List<KnowledgeCase> candidates = buildCases(event, scoreItemsForGate, matchedSlices, ctx, bidDoc);
            List<KnowledgeCase> existing = knowledgeCaseRepository.findBySourceProjectId(projectId);
            java.util.Set<Long> existingPointIds = existing.stream()
                    .map(KnowledgeCase::getScoringPointId)
                    .filter(java.util.Objects::nonNull)
                    .collect(java.util.stream.Collectors.toSet());

            List<KnowledgeCase> casesToSave = candidates.stream()
                    .filter(c -> c.getScoringPointId() == null || !existingPointIds.contains(c.getScoringPointId()))
                    .toList();

            Boolean accepted = transactionTemplate.execute(status -> {
                if (!taskStateService.complete(executionTaskId, casesToSave.size())) return false;
                if (!casesToSave.isEmpty()) knowledgeCaseRepository.saveAll(casesToSave);
                return true;
            });
            if (!Boolean.TRUE.equals(accepted)) {
                log.info("Discard stale case precipitation result task={}", executionTaskId);
                return;
            }
            if (!casesToSave.isEmpty()) {
                log.info("Successfully persisted {} new knowledge cases for project: {} ({} skipped as duplicate)",
                        casesToSave.size(), projectId, candidates.size() - casesToSave.size());
            }
            notifier.notifySuccess(projectId, event.getProjectName(), casesToSave.size(),
                    System.currentTimeMillis() - startTime, ctx.projectOpt.orElse(null), event.getTriggerUserId());

        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            if (isKnowledgeCaseDedupConflict(e)) {
                log.info("Knowledge cases for project {} already persisted by concurrent event, treated as idempotent success", projectId);
                taskStateService.complete(executionTaskId, 0);
                notifier.notifySuccess(projectId, event.getProjectName(), 0,
                        System.currentTimeMillis() - startTime, project, event.getTriggerUserId());
            } else {
                log.error("Knowledge case persistence failed for project {}", projectId, e);
                taskStateService.fail(executionTaskId, "案例沉淀失败，请稍后重试");
                notifier.notifyFailure(projectId, event.getProjectName(), "案例保存失败，请联系管理员",
                        project, event.getTriggerUserId());
            }
        } catch (RuntimeException e) {
            log.error("Failed to process project closed AI slicing", e);
            String reason = e.getMessage() != null ? e.getMessage() : "处理异常，请检查系统日志";
            taskStateService.fail(executionTaskId, "案例沉淀失败，请稍后重试");
            try {
                notifier.notifyFailure(projectId, event.getProjectName(), reason, project, event.getTriggerUserId());
            } catch (RuntimeException notifyEx) {
                log.error("Failed to send failure notification for project {}", projectId, notifyEx);
            }
        }
    }

    private boolean isKnowledgeCaseDedupConflict(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            String message = current.getMessage();
            if (message != null && message.contains("uk_source_proj_scoring_point")) return true;
        }
        return false;
    }

    private CasePrecipitationContext loadProjectContext(Long projectId) {
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

    private List<ScoreItemSnapshot> loadScoreItemSnapshots(Long projectId) {
        return scoreItemRepository.findByProjectIdOrderByItemIndexAsc(projectId).stream()
                .map(item -> new ScoreItemSnapshot(item.getId(), item.getDim(), item.getDetail(), item.getDim()))
                .toList();
    }

    private List<KnowledgeCase> buildCases(ProjectClosedEvent event, List<ScoreItemSnapshot> scoreItems,
                                            List<CaseAiMatcher.AiMatchedSlice> matchedSlices,
                                            CasePrecipitationContext ctx,
                                            BidDocumentLoader.BidDocument bidDoc) {
        Long projectId = event.getProjectId();
        List<KnowledgeCase> casesToSave = new ArrayList<>();
        MarkdownSourceSectionIndex sectionIndex = null;
        if (bidDoc != null && bidDoc.markdown() != null && !bidDoc.markdown().isBlank()) {
            try {
                sectionIndex = MarkdownSourceSectionIndex.parse(bidDoc.markdown());
            } catch (RuntimeException e) {
                log.warn("Markdown 章节索引解析失败，沉淀将降级为无来源标题: {}", e.getMessage());
            }
        }

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

            try {
                MarkdownSourceSectionIndex.Provenance prov = slice.getProvenance();
                if (prov == null && sectionIndex != null
                        && slice.getMatchedSnippet() != null && !slice.getMatchedSnippet().isBlank()) {
                    prov = sectionIndex.locate(slice.getMatchedSnippet(), scoreItem.title());
                }
                if (prov != null) {
                    kCase.setSourceSectionTitle(prov.title());
                    kCase.setSourceSectionPath(prov.path());
                    kCase.setSourceDocumentName(bidDoc != null ? bidDoc.documentName() : null);
                    kCase.setSourcePageNumber(prov.pageNumber() != null
                            ? prov.pageNumber() : (bidDoc != null ? bidDoc.pageNumber() : null));
                    kCase.setSourceSectionIndex(prov.index());
                }
            } catch (RuntimeException e) {
                log.warn("来源章节定位失败，降级为 NULL: {}", e.getMessage());
            }

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
