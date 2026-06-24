// Input: TenderEvaluationRepository, TenderRepository, ProjectRepository, TaskService, UserRepository
// Output: TenderEvaluation operations - admin review, proceed-to-bid + V119 facade
// Pos: Service/业务编排层
// 维护声明: 仅维护标讯评估业务规则。V118 facade (submitEvaluation) 已 retired (V119 移除)。
package com.xiyu.bid.tender.service;

import com.xiyu.bid.batch.core.TenderStatusTransitionPolicy;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.exception.ResourceNotFoundException;
import com.xiyu.bid.project.dto.ProjectDTO;
import com.xiyu.bid.project.service.ProjectService;
import com.xiyu.bid.repository.TaskRepository;
import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.tender.controller.TenderEvaluationController.TenderBidResult;
import com.xiyu.bid.tender.dto.TenderEvaluationDTO;
import com.xiyu.bid.tender.dto.TenderEvaluationSubmitRequest;
import com.xiyu.bid.tender.dto.TenderReviewRequest;
import com.xiyu.bid.tender.entity.TenderEvaluation;
import com.xiyu.bid.tender.repository.TenderEvaluationRepository;
import com.xiyu.bid.projectworkflow.entity.ProjectDocument;
import com.xiyu.bid.task.dto.TaskDTO;
import com.xiyu.bid.task.service.TaskService;
import com.xiyu.bid.webhook.domain.TenderStatusChangedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 标讯评估服务（V130 三段式重构）。
 * <p>当前职责：
 * <ul>
 *   <li>V119 评估表草稿 / 提交（委托给 {@link TenderEvaluationSubmissionService}）</li>
 *   <li>V130 审核确认（委托给 {@link TenderEvaluationReviewService}）</li>
 *   <li>管理员审核（投标 / 弃标）</li>
 *   <li>投标立项（创建项目 + 待办）</li>
 * </ul>
 */
@Service
@Slf4j
@Transactional
public class TenderEvaluationService {

    private final TenderEvaluationRepository tenderEvaluationRepository;
    private final TenderRepository tenderRepository;
    private final ProjectService projectService;
    private final TaskService taskService;
    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final TenderEvaluationSubmissionService submissionService;
    private final TenderAssignmentPermissions permissions;
    private final TenderProjectAccessGuard accessGuard;
    private final ApplicationEventPublisher eventPublisher;
    private final TenderEvaluationDocumentService documentService;
    private final InitiationPrefillService initiationPrefillService;
    private final TenderBidTaskFactory bidTaskFactory;
    private final TenderEvaluationSubmissionMapper mapper = new TenderEvaluationSubmissionMapper();

    public TenderEvaluationService(
            TenderEvaluationRepository tenderEvaluationRepository,
            TenderRepository tenderRepository,
            ProjectService projectService,
            TaskService taskService,
            TaskRepository taskRepository,
            UserRepository userRepository,
            TenderEvaluationSubmissionService submissionService,
            TenderAssignmentPermissions permissions,
            TenderProjectAccessGuard accessGuard,
            ApplicationEventPublisher eventPublisher,
            TenderEvaluationDocumentService documentService,
            InitiationPrefillService initiationPrefillService,
            TenderBidTaskFactory bidTaskFactory) {
        this.tenderEvaluationRepository = tenderEvaluationRepository;
        this.tenderRepository = tenderRepository;
        this.projectService = projectService;
        this.taskService = taskService;
        this.taskRepository = taskRepository;
        this.userRepository = userRepository;
        this.submissionService = submissionService;
        this.permissions = permissions;
        this.accessGuard = accessGuard;
        this.eventPublisher = eventPublisher;
        this.documentService = documentService;
        this.initiationPrefillService = initiationPrefillService;
        this.bidTaskFactory = bidTaskFactory;
    }

    // ---------- V119: 项目评估表草稿/提交 facade（委托给 TenderEvaluationSubmissionService） ----------

    /** 加载或初始化草稿（V119）。 */
    public TenderEvaluationDTO loadOrInitDraft(Long tenderId, Long evaluatorId) {
        return submissionService.loadOrInitDraft(tenderId, evaluatorId);
    }

    /** 保存草稿（V119 / V130 三段式）。 */
    public TenderEvaluationDTO saveDraft(Long tenderId,
                                         TenderEvaluationSubmitRequest request,
                                         Long evaluatorId) {
        return submissionService.saveDraft(tenderId, request, evaluatorId);
    }

    /** 提交评估（V119 / V130 三段式）。 */
    public TenderEvaluationDTO submit(Long tenderId,
                                      TenderEvaluationSubmitRequest request,
                                      Long evaluatorId) {
        return submissionService.submit(tenderId, request, evaluatorId);
    }

    /**
     * 获取标讯评估详情（旧 API，已无前端调用方；保留以避免破坏单测）。
     * <p>实例级权限标志默认为 false：本路径不携带 userId，不能做相对判定。
     */
    @Transactional(readOnly = true)
    public Optional<TenderEvaluationDTO> getEvaluation(Long tenderId) {
        return tenderEvaluationRepository.findByTenderId(tenderId)
                .map(e -> {
                    Tender t = tenderRepository.findById(e.getTenderId()).orElse(null);
                    return toDTO(e, t, false, false);
                });
    }

    // ========== 原有方法（未变更） ==========

    /**
     * 决策标讯（投标 / 弃标）—— 不再依赖角色 enum；改用实例级 canDecide 判定。
     */
    public TenderEvaluationDTO reviewTender(Long tenderId, TenderReviewRequest request, Long reviewerId) {
        log.info("Reviewing tender {} by user {}, approved={}", tenderId, reviewerId, request.approved());

        Tender tender = tenderRepository.findById(tenderId)
                .orElseThrow(() -> new ResourceNotFoundException("Tender", tenderId.toString()));
        accessGuard.assertCanAccessTender(tender);

        if (tender.getStatus() != Tender.Status.EVALUATED) {
            throw new IllegalStateException("标讯状态已变更，无法执行该操作");
        }

        if (!permissions.canDecide(tenderId, reviewerId)) {
            throw new AccessDeniedException(
                    "user " + reviewerId + " is not the assigner of tender " + tenderId);
        }

        TenderEvaluation evaluation = tenderEvaluationRepository.findByTenderId(tenderId)
                .orElseThrow(() -> new ResourceNotFoundException("标讯尚未提交评估"));

        // 如果评估表处于待审核状态，不允许直接投标/弃标决策
        if (evaluation.isRequiresReview()) {
            throw new IllegalArgumentException("评估表已被重新编辑，请先确认审核后再执行投标/弃标操作");
        }

        User reviewer = userRepository.findById(reviewerId)
                .orElseThrow(() -> new ResourceNotFoundException("User", reviewerId.toString()));

        evaluation.setReviewStatus(request.approved()
                ? TenderEvaluation.ReviewStatus.APPROVED
                : TenderEvaluation.ReviewStatus.REJECTED);
        evaluation.setReviewerId(reviewerId);
        evaluation.setReviewerName(reviewer.getUsername());
        evaluation.setReviewedAt(LocalDateTime.now());
        evaluation.setReviewComment(request.reviewComment());

        TenderEvaluation savedEvaluation = tenderEvaluationRepository.save(evaluation);

        if (request.approved()) {
            TenderStatusTransitionPolicy.assertTransition(tender.getStatus(), Tender.Status.BIDDING);
            tender.setStatus(Tender.Status.BIDDING);
        } else {
            TenderStatusTransitionPolicy.assertTransition(tender.getStatus(), Tender.Status.ABANDONED);
            tender.setStatus(Tender.Status.ABANDONED);
            if (request.abandonmentReason() != null && !request.abandonmentReason().isBlank()) {
                tender.setAbandonmentReason(request.abandonmentReason());
            }
            // When abandoning, reset evaluation to DRAFT for potential re-evaluation
            if (evaluation != null && evaluation.getEvaluationStatus() == TenderEvaluation.EvaluationStatus.SUBMITTED) {
                evaluation.setEvaluationStatus(TenderEvaluation.EvaluationStatus.DRAFT);
                evaluation.setSubmittedAt(null);
                evaluation.setReviewStatus(TenderEvaluation.ReviewStatus.PENDING);
                tenderEvaluationRepository.save(evaluation);
            }
        }
        tenderRepository.save(tender);

        // CO-229: 审批确认后发布状态变更事件，触发 CRM webhook 回调
        Boolean recShouldBid = null;
        String recReason = null;
        if (evaluation.getBidRecommendation() != null) {
            recShouldBid = evaluation.getBidRecommendation() == TenderEvaluation.BidRecommendation.RECOMMEND;
        }
        if (evaluation.getRecommendation() != null) {
            recReason = evaluation.getRecommendation().getReason();
        }
        eventPublisher.publishEvent(TenderStatusChangedEvent.of(
                tender.getId(), tender.getExternalId(),
                Tender.Status.EVALUATED, tender.getStatus(), tender.getTitle(),
                request.abandonmentReason(),
                reviewerId, reviewer.getUsername(),
                recShouldBid, recReason));

        log.info("Tender {} reviewed, status changed to {}", tenderId, tender.getStatus());
        boolean canFill = permissions.canFill(tenderId, reviewerId);
        boolean canDecide = permissions.canDecide(tenderId, reviewerId);
        return toDTO(savedEvaluation, tender, canFill, canDecide);
    }

    /**
     * 投标立项：标讯 BIDDING 后创建项目和待办。兼容评估-审核流程与详情页快速投标流程。
     */
    public TenderBidResult proceedToBid(Long tenderId, Long adminId) {
        log.info("Proceeding to bid for tender {} by user {}", tenderId, adminId);

        Tender tender = tenderRepository.findById(tenderId)
                .orElseThrow(() -> new ResourceNotFoundException("Tender", tenderId.toString()));
        accessGuard.assertCanAccessTender(tender);

        if (!permissions.canDecide(tenderId, adminId)) {
            throw new AccessDeniedException(
                    "user " + adminId + " is not the assigner of tender " + tenderId);
        }

        if (tender.getStatus() != Tender.Status.BIDDING) {
            throw new IllegalStateException("标讯状态不是已投标，无法创建立项待办");
        }

        var evaluationOpt = tenderEvaluationRepository.findByTenderId(tenderId);
        Long projectManagerId = evaluationOpt
                .map(TenderEvaluation::getEvaluatorId)
                .filter(Objects::nonNull)
                .orElse(adminId);

        ProjectDTO projectDTO = ProjectDTO.builder()
                .name(tender.getTitle())
                .tenderId(tenderId)
                .status(Project.Status.PENDING_INITIATION)
                .managerId(projectManagerId)
                .customer(tender.getPurchaserName())
                .budget(tender.getBudget())
                .region(tender.getRegion())
                .industry(tender.getIndustry())
                .customerType(tender.getCustomerType())
                .deadline(tender.getDeadline() != null ? tender.getDeadline().toLocalDate() : null)
                .description(tender.getDescription())
                .tagsJson(tender.getTags())
                .platform(tender.getSourcePlatform() != null ? tender.getSourcePlatform() : tender.getSource())
                .build();
        ProjectDTO createdProject = projectService.createProject(projectDTO);
        tender.setProjectId(createdProject.getId());
        tenderRepository.save(tender);

        // CO-323: 评估数据带入立项（幂等，无评估数据则跳过；预填失败不阻塞投标流程 FR-005）
        try {
            initiationPrefillService.prefillFromEvaluation(createdProject.getId(), tenderId, evaluationOpt.orElse(null));
        } catch (RuntimeException ex) {
            log.warn("CO-323: prefill initiation failed for tender {}, non-blocking", tenderId, ex);
        }

        TaskDTO createdTask = bidTaskFactory.reuseOrCreate(
                tenderId, createdProject.getId(), tender.getTitle(), projectManagerId);

        log.info("Project {} and task {} created for tender {}", createdProject.getId(), createdTask.getId(), tenderId);
        return new TenderBidResult(
                createdProject.getId(),
                createdProject.getName(),
                createdTask.getId(),
                createdTask.getTitle()
        );
    }

    // ---------- DTO 转换（V130 三段式） ----------

    /** CO-262: 转换 DTO 时同步加载 GAP 附件回填到 evaluationBasic.projectPlanGapFiles。 */
    private TenderEvaluationDTO toDTO(TenderEvaluation evaluation, Tender tender, boolean canFill, boolean canDecide) {
        List<ProjectDocument> gapFiles = documentService.getDocuments(evaluation.getTenderId());
        return mapper.toDTO(evaluation, tender, canFill, canDecide, gapFiles);
    }
}
