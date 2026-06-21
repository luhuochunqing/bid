// Input: 项目 id、leads 请求、当前用户；依赖 ProjectLeadAssignmentRepository + TaskRepository + ProjectStageService
// Output: ProjectDraftingViewDto；纯编排，核心规则委托给 AllTasksCompletedPolicy / BidReviewPolicy；§3.6 CLOSED 全字段锁定
// Pos: project/service/ - 编排层
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.project.service;

import com.xiyu.bid.annotation.Auditable;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.entity.Task;
import com.xiyu.bid.exception.ResourceNotFoundException;
import com.xiyu.bid.project.core.AllTasksCompletedPolicy;
import com.xiyu.bid.project.core.BidReviewPolicy;
import com.xiyu.bid.project.core.BidReviewStatus;
import com.xiyu.bid.project.core.BidSubmissionAuthorizationPolicy;
import com.xiyu.bid.project.core.ProjectFieldLockPolicy;
import com.xiyu.bid.project.core.EvaluationSubStage;
import com.xiyu.bid.project.core.ProjectStage;
import com.xiyu.bid.project.core.ProjectStageTransitionPolicy;
import com.xiyu.bid.project.dto.ProjectDraftingViewDto;
import com.xiyu.bid.project.dto.ProjectLeadAssignmentRequest;
import com.xiyu.bid.project.entity.ProjectEvaluation;
import com.xiyu.bid.project.entity.ProjectLeadAssignment;
import com.xiyu.bid.project.notification.ProjectNotificationService;
import com.xiyu.bid.project.repository.ProjectEvaluationRepository;
import com.xiyu.bid.project.repository.ProjectLeadAssignmentRepository;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.repository.TaskRepository;
import com.xiyu.bid.service.ProjectAccessScopeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import com.xiyu.bid.entity.RoleProfileCatalog;

/**
 * PRD §3.2 标书编制阶段编排服务：主/副负责人分配 + 标书审核 + 提交闸门。
 * <p>不持有任务 CRUD 逻辑，仅读取任务状态委托 {@link AllTasksCompletedPolicy}。</p>
 * <p>标书审核流程委托给 {@link BidReviewAppService}。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ProjectDraftingService {

    private final ProjectLeadAssignmentRepository leadRepo;
    private final ProjectRepository projectRepository;
    private final TaskRepository taskRepository;
    private final ProjectStageService projectStageService;
    private final ProjectAccessScopeService projectAccessScopeService;
    private final UserRepository userRepository;
    private final BidReviewAppService bidReviewAppService;
    private final ProjectEvaluationRepository projectEvaluationRepository;
    private final ProjectNotificationService notificationService;

    @Auditable(action = "ASSIGN_PROJECT_LEADS", entityType = "ProjectLeadAssignment",
            description = "分配主/副投标负责人")
    public ProjectDraftingViewDto assignLeads(
            Long projectId, ProjectLeadAssignmentRequest req, Long currentUserId) {
        mustGetProject(projectId);
        ProjectStage stage = projectStageService.currentStage(projectId);
        var lockDecision = ProjectFieldLockPolicy.assertWritable(stage, "leads");
        if (!lockDecision.allowed()) {
            var deny = (ProjectFieldLockPolicy.Decision.Deny) lockDecision;
            throw new ResponseStatusException(HttpStatus.LOCKED, deny.reason());
        }
        if (req == null || req.getPrimaryLeadUserId() == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "主投标负责人不能为空");
        }
        if (req.getSecondaryLeadUserId() != null
                && req.getSecondaryLeadUserId().equals(req.getPrimaryLeadUserId())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "主/副负责人不能相同");
        }
        ProjectLeadAssignment entity = leadRepo.findByProjectId(projectId)
                .orElseGet(() -> ProjectLeadAssignment.builder().projectId(projectId).build());
        entity.setPrimaryLeadUserId(req.getPrimaryLeadUserId());
        entity.setSecondaryLeadUserId(req.getSecondaryLeadUserId());
        entity.setAssignedAt(LocalDateTime.now());
        entity.setAssignedBy(currentUserId);
        ProjectLeadAssignment saved = leadRepo.save(entity);
        log.info("Project leads assigned project={} primary={} secondary={} by={}",
                projectId, saved.getPrimaryLeadUserId(), saved.getSecondaryLeadUserId(), currentUserId);
        return toView(projectId, saved);
    }

    /**
     * §3.2.3 闸门检查。
     */
    @Auditable(action = "GATE_ADVANCE_TO_EVALUATION", entityType = "Project",
            description = "DRAFTING → EVALUATING 闸门检查")
    public ProjectDraftingViewDto gateAdvanceToEvaluation(Long projectId, Long currentUserId) {
        mustGetProject(projectId);
        AllTasksCompletedPolicy.Decision d = gateDecision(projectId);
        if (!d.allowed()) {
            int incomplete = ((AllTasksCompletedPolicy.Decision.Deny) d).incompleteCount();
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "仍有 " + incomplete + " 个任务未完成，无法推进到评标");
        }
        ProjectLeadAssignment lead = leadRepo.findByProjectId(projectId).orElse(null);
        return toView(projectId, lead);
    }

    // ── 标书审核流程（委托给 BidReviewAppService）────────────────────────

    public ProjectDraftingViewDto submitForReview(Long projectId, Long reviewerId, Long currentUserId) {
        projectAccessScopeService.assertCurrentUserCanAccessProject(projectId);
        mustGetProject(projectId);
        bidReviewAppService.submitForReview(projectId, reviewerId, currentUserId);
        ProjectLeadAssignment lead = leadRepo.findByProjectId(projectId).orElse(null);
        return toView(projectId, lead);
    }

    public ProjectDraftingViewDto approveBid(Long projectId, Long currentUserId, String comment) {
        projectAccessScopeService.assertCurrentUserCanAccessProject(projectId);
        mustGetProject(projectId);
        bidReviewAppService.approveBid(projectId, currentUserId, comment);
        ProjectLeadAssignment lead = leadRepo.findByProjectId(projectId).orElse(null);
        return toView(projectId, lead);
    }

    public ProjectDraftingViewDto rejectBid(Long projectId, Long currentUserId, String reason) {
        projectAccessScopeService.assertCurrentUserCanAccessProject(projectId);
        mustGetProject(projectId);
        bidReviewAppService.rejectBid(projectId, currentUserId, reason);
        ProjectLeadAssignment lead = leadRepo.findByProjectId(projectId).orElse(null);
        return toView(projectId, lead);
    }

    /**
     * 提交投标：审核通过 + 闸门通过后推进到 EVALUATING 阶段。
     */
    @Auditable(action = "SUBMIT_BID", entityType = "Project",
            description = "提交投标并推进到评标阶段")
    public ProjectDraftingViewDto submitBid(Long projectId, Long currentUserId) {

        // 业务角色校验：仅投标系统管理员/投标管理员/投标组长/投标项目负责人/投标专员可以提交投标
        // （通过 roleProfile.code 判断，不回退到 User.role 枚举）
        User currentUser = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在"));

        // 直接使用 roleProfile.code，不经过 RoleProfileCatalog.definitionForCode 的 fallback
        // （definitionForCode 对未注册 code 会 fallback 到 admin，会导致 admin_staff 等角色被误判为 admin）
        String effectiveRoleCode = currentUser.getRoleProfile() != null
                ? currentUser.getRoleProfile().getCode()
                : null;

        // 项目级投标负责人分配（同时用于权限校验和返回视图，避免重复查询）
        ProjectLeadAssignment lead = leadRepo.findByProjectId(projectId).orElse(null);

        // 纯核心授权决策：角色白名单 + 项目级负责人匹配
        BidSubmissionAuthorizationPolicy.Decision authDecision =
                BidSubmissionAuthorizationPolicy.canSubmitBid(effectiveRoleCode, currentUserId, lead);
        if (!authDecision.allowed()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, authDecision.reason());
        }

        projectAccessScopeService.assertCurrentUserCanAccessProject(projectId);
        mustGetProject(projectId);

        // 校验审核是否已通过
        var reviewState = bidReviewAppService.getReviewState(projectId);
        var reviewDecision = BidReviewPolicy.canSubmitBid(parseStatus(reviewState.status()));
        if (!reviewDecision.allowed()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, reviewDecision.reason());
        }

        // 校验任务闸门
        AllTasksCompletedPolicy.Decision d = gateDecision(projectId);
        if (!d.allowed()) {
            int incomplete = ((AllTasksCompletedPolicy.Decision.Deny) d).incompleteCount();
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "仍有 " + incomplete + " 个任务未完成，无法提交投标");
        }

        ProjectStage currentStage = projectStageService.currentStage(projectId);
        if (currentStage != ProjectStage.DRAFTING) {
            log.info("Bid submission skipped (idempotent) project={} currentStage={}",
                    projectId, currentStage);
            return toView(projectId, lead);
        }
        projectStageService.requestTransition(projectId, ProjectStage.EVALUATING,
                ProjectStageTransitionPolicy.GateInputs.EMPTY);
        ensureEvaluationInitialized(projectId, currentUserId);

        // 通知 #10: 提交投标→进入评标 → 团队成员
        notificationService.notifyStageTransition(projectId, ProjectStage.DRAFTING, ProjectStage.EVALUATING);

        log.info("Bid submitted project={} stage={}->EVALUATING user={}",
                projectId, currentStage, currentUserId);
        return toView(projectId, lead);
    }

    @Transactional(readOnly = true)
    public ProjectDraftingViewDto get(Long projectId) {
        mustGetProject(projectId);
        ProjectLeadAssignment lead = leadRepo.findByProjectId(projectId).orElse(null);
        return toView(projectId, lead);
    }

    // ── 辅助方法 ──────────────────────────────────────────────────────────

    private AllTasksCompletedPolicy.Decision gateDecision(Long projectId) {
        List<Task> tasks = taskRepository.findByProjectId(projectId);
        List<AllTasksCompletedPolicy.TaskState> states = tasks.stream()
                .map(t -> t.getStatus() == null
                        ? AllTasksCompletedPolicy.TaskState.TODO
                        : AllTasksCompletedPolicy.TaskState.valueOf(t.getStatus().name()))
                .toList();
        return AllTasksCompletedPolicy.decide(states);
    }

    private Project mustGetProject(Long projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", String.valueOf(projectId)));
    }

    private void ensureEvaluationInitialized(Long projectId, Long userId) {
        if (projectEvaluationRepository.findByProjectId(projectId).isPresent()) {
            return;
        }
        ProjectEvaluation evaluation = ProjectEvaluation.builder()
                .projectId(projectId)
                .subStage(EvaluationSubStage.IN_PROGRESS.name())
                .evaluationStartedAt(LocalDateTime.now())
                .notes("")
                .createdBy(userId)
                .updatedBy(userId)
                .build();
        projectEvaluationRepository.save(evaluation);
        log.info("ProjectEvaluation initialized on bid submission project={} user={}", projectId, userId);
    }

    private static BidReviewStatus parseStatus(String raw) {
        if (raw == null) return null;
        try {
            return BidReviewStatus.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private ProjectDraftingViewDto toView(Long projectId, ProjectLeadAssignment lead) {
        AllTasksCompletedPolicy.Decision decision = gateDecision(projectId);
        int incomplete = decision instanceof AllTasksCompletedPolicy.Decision.Deny d
                ? d.incompleteCount() : 0;

        // 读取审核状态（委托给 BidReviewAppService）
        var reviewState = bidReviewAppService.getReviewState(projectId);

        // 判断是否已提交投标（推进到评标阶段）
        ProjectStage currentStage = projectStageService.currentStage(projectId);
        boolean bidSubmitted = currentStage != ProjectStage.DRAFTING;

        return ProjectDraftingViewDto.builder()
                .projectId(projectId)
                .primaryLeadUserId(lead == null ? null : lead.getPrimaryLeadUserId())
                .secondaryLeadUserId(lead == null ? null : lead.getSecondaryLeadUserId())
                .incompleteTaskCount(incomplete)
                .gateReady(decision.allowed())
                .reviewStatus(reviewState.status())
                .reviewerId(reviewState.reviewerId())
                .reviewerName(reviewState.reviewerName())
                .rejectReason(reviewState.rejectReason())
                .bidSubmitted(bidSubmitted)
                .build();
    }
}
