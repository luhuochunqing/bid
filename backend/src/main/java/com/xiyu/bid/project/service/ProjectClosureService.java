// Input: 结项请求 + 保证金快照 + 闸门策略
// Output: ClosureDTO / ClosurePreviewDTO；通过策略校验 + 持久化 + 审计 + 审核流程；蓝图 §3.3.1.6
// Pos: project/service/ - 编排层（不含纯规则）
package com.xiyu.bid.project.service;

import com.xiyu.bid.annotation.Auditable;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.exception.ResourceNotFoundException;
import com.xiyu.bid.project.core.ProjectClosureGatePolicy;
import com.xiyu.bid.project.core.ProjectClosureGatePolicy.ClosureInput;
import com.xiyu.bid.project.core.ProjectStage;
import com.xiyu.bid.project.core.ProjectStageTransitionPolicy;
import com.xiyu.bid.project.dto.ClosureDTO;
import com.xiyu.bid.project.dto.ClosurePreviewDTO;
import com.xiyu.bid.project.dto.ClosureSubmitRequest;
import com.xiyu.bid.project.entity.ProjectClosure;
import com.xiyu.bid.project.entity.ProjectDepositSnapshot;
import com.xiyu.bid.project.repository.ProjectClosureRepository;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.notification.core.NotificationType;
import com.xiyu.bid.notification.dto.CreateNotificationRequest;
import com.xiyu.bid.notification.service.NotificationApplicationService;
import com.xiyu.bid.project.service.ProjectClosureDepositAssembler.DepositStatusInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ProjectClosureService {

    private final ProjectClosureRepository closureRepository;
    private final ProjectRepository projectRepository;
    private final ProjectStageService projectStageService;
    private final ProjectClosureDepositAssembler depositAssembler;
    private final UserRepository userRepository;
    private final NotificationApplicationService notificationService;

    @Transactional(readOnly = true)
    public ClosurePreviewDTO preview(Long projectId) {
        mustGetProject(projectId);
        Optional<ProjectClosure> existingClosure = closureRepository.findByProjectId(projectId);
        ProjectDepositSnapshot snap = depositAssembler.buildSnapshot(projectId, existingClosure);
        var gateSnap = depositAssembler.mapToGateSnapshot(snap, existingClosure.orElse(null));
        boolean alreadyClosed = closureRepository.existsByProjectIdAndStageLockedTrue(projectId);
        var decision = ProjectClosureGatePolicy.decide(gateSnap, ClosureInput.EMPTY);
        List<String> blockingReasons = decision.allowed() ? List.of()
                : ((ProjectClosureGatePolicy.Decision.Deny) decision).reasons();
        ProjectClosure closure = existingClosure.orElse(null);
        boolean canClose = decision.allowed() && !alreadyClosed
                && (closure == null || !"PENDING".equals(closure.getReviewStatus()));
        String paymentMethod = depositAssembler.getPaymentMethod(projectId);
        return ClosurePreviewDTO.builder()
                .projectId(projectId).hasDeposit(snap.hasDeposit()).depositAmount(snap.depositAmount())
                .depositPaymentMethod(snap.hasDeposit() ? paymentMethod : null)
                .depositReturnStatus(snap.returnStatus().name())
                .depositReturnDate(snap.returnDate()).depositReturnEvidenceId(snap.evidenceDocId())
                .transferAmount(closure != null ? closure.getTransferAmount() : null)
                .returnedAmount(closure != null ? closure.getReturnedAmount() : null)
                .canClose(canClose).blockingReasons(blockingReasons)
                .alreadyClosed(alreadyClosed).stageLocked(alreadyClosed)
                .reviewStatus(closure != null ? closure.getReviewStatus() : "DRAFT")
                .projectSummary(closure != null ? closure.getProjectSummary() : null)
                .rejectionReason(closure != null ? closure.getRejectionReason() : null)
                .reviewedBy(closure != null ? closure.getReviewedBy() : null)
                .reviewedAt(closure != null ? closure.getReviewedAt() : null).build();
    }

    @Auditable(action = "PROJECT_CLOSURE_SUBMITTED", entityType = "ProjectClosure", description = "提交项目结项申请")
    public ClosureDTO submitClosure(Long projectId, ClosureSubmitRequest req, Long userId) {
        mustGetProject(projectId);
        if (closureRepository.existsByProjectIdAndStageLockedTrue(projectId)) {
            throw new ResponseStatusException(HttpStatus.LOCKED, "项目已结项，不可重复操作");
        }
        Optional<ProjectClosure> existingClosure = closureRepository.findByProjectId(projectId);
        ProjectDepositSnapshot depositSnap = depositAssembler.buildSnapshot(projectId, existingClosure);
        DepositStatusInfo statusInfo = depositAssembler.resolveStatus(req, depositSnap);
        var gateSnap = new ProjectClosureGatePolicy.DepositSnapshot(
                depositSnap.hasDeposit(), statusInfo.status(),
                statusInfo.returnDate(), statusInfo.evidenceDocId(),
                statusInfo.transferAmount(), statusInfo.returnedAmount());
        var decision = ProjectClosureGatePolicy.decide(gateSnap, new ClosureInput(req.getArchiveLocation(), req.getNotes()));
        if (!decision.allowed()) {
            var deny = (ProjectClosureGatePolicy.Decision.Deny) decision;
            throw new ResponseStatusException(HttpStatus.CONFLICT, deny.reasonText());
        }
        LocalDateTime now = LocalDateTime.now();
        ProjectClosure entity = existingClosure
                .orElseGet(() -> ProjectClosure.builder().projectId(projectId).createdBy(userId).build());
        entity.setDepositReturnStatus(statusInfo.status().name());
        entity.setDepositReturnDate(statusInfo.returnDate());
        entity.setDepositReturnEvidenceId(statusInfo.evidenceDocId());
        entity.setTransferAmount(statusInfo.transferAmount());
        entity.setReturnedAmount(statusInfo.returnedAmount());
        entity.setArchiveLocation(req.getArchiveLocation());
        entity.setProjectSummary(req.getProjectSummary());
        entity.setNotes(req.getNotes());
        entity.setReviewStatus("PENDING");
        entity.setUpdatedBy(userId);
        ProjectClosure saved = closureRepository.save(entity);
        log.info("Closure submitted for review: projectId={} userId={}", projectId, userId);

        // 通知 #16: 提交结项申请 → admin/bid_admin/bid_lead/bid_senior
        sendClosureSubmitNotification(projectId, userId);

        return toDto(saved);
    }

    @Auditable(action = "PROJECT_CLOSURE_APPROVED", entityType = "ProjectClosure", description = "审核通过项目结项")
    public ClosureDTO approveClosure(Long projectId, Long userId) {
        mustGetProject(projectId);
        ProjectClosure closure = closureRepository.findByProjectId(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "未找到结项申请，请先提交结项"));
        if (!"PENDING".equals(closure.getReviewStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "结项申请状态不是待审核，无法通过");
        }
        LocalDateTime now = LocalDateTime.now();
        closure.setReviewStatus("APPROVED");
        closure.setReviewedBy(userId);
        closure.setReviewedAt(now);
        closure.setClosedAt(now);
        closure.setClosedBy(userId);
        closure.setStageLocked(true);
        closure.setUpdatedBy(userId);
        ProjectClosure saved = closureRepository.save(closure);
        ProjectStage current = projectStageService.currentStage(projectId);
        if (current != ProjectStage.CLOSED) {
            projectStageService.requestTransition(projectId, ProjectStage.CLOSED,
                    ProjectStageTransitionPolicy.GateInputs.EMPTY);
        }
        log.info("Closure approved: projectId={} userId={}", projectId, userId);

        // 通知 #17: 结项审核通过 → 提交人
        sendClosureReviewNotification(projectId, closure.getCreatedBy(), true, null, userId);

        return toDto(saved);
    }

    @Auditable(action = "PROJECT_CLOSURE_REJECTED", entityType = "ProjectClosure", description = "驳回项目结项申请")
    public ClosureDTO rejectClosure(Long projectId, String reason, Long userId) {
        mustGetProject(projectId);
        ProjectClosure closure = closureRepository.findByProjectId(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "未找到结项申请，请先提交结项"));
        if (!"PENDING".equals(closure.getReviewStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "结项申请状态不是待审核，无法驳回");
        }
        LocalDateTime now = LocalDateTime.now();
        closure.setReviewStatus("REJECTED");
        closure.setRejectionReason(reason);
        closure.setReviewedBy(userId);
        closure.setReviewedAt(now);
        closure.setUpdatedBy(userId);
        log.info("Closure rejected: projectId={} userId={} reason={}", projectId, userId, reason);

        // 通知 #17: 结项审核驳回 → 提交人
        sendClosureReviewNotification(projectId, closure.getCreatedBy(), false, reason, userId);

        return toDto(closureRepository.save(closure));
    }

    /**
     * 二次招标：基于已结项项目创建新项目。
     * 自动带入招标主体、客户信息等字段，重新走立项流程。
     */
    @Auditable(action = "PROJECT_REBID_CREATED", entityType = "Project", description = "二次招标创建新项目")
    @Transactional
    public Long rebidProject(Long projectId, Long userId) {
        Project oldProject = mustGetProject(projectId);
        ProjectClosure closure = closureRepository.findByProjectId(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "未找到结项记录"));
        if (!"APPROVED".equals(closure.getReviewStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "项目尚未结项，无法二次招标");
        }

        // 复制基础信息创建新项目
        Project newProject = new Project();
        newProject.setName(oldProject.getName() + "（二次招标）");
        newProject.setTenderId(oldProject.getTenderId());
        newProject.setStage("INITIATED");
        newProject.setManagerId(userId);
        newProject.setCustomer(oldProject.getCustomer());
        newProject.setCustomerType(oldProject.getCustomerType());
        newProject.setIndustry(oldProject.getIndustry());
        newProject.setRegion(oldProject.getRegion());
        newProject.setPlatform(oldProject.getPlatform());
        newProject.setSourceModule("REBID");
        newProject.setSourceCustomer(oldProject.getSourceCustomer());
        newProject.setSourceCustomerId(oldProject.getSourceCustomerId());
        newProject.setStartDate(java.time.LocalDateTime.now());

        Project saved = projectRepository.save(newProject);
        log.info("Rebid project created: oldProjectId={} newProjectId={} userId={}", projectId, saved.getId(), userId);
        return saved.getId();
    }

    private Project mustGetProject(Long projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", String.valueOf(projectId)));
    }

    private ClosureDTO toDto(ProjectClosure e) {
        return ClosureDTO.builder().id(e.getId()).projectId(e.getProjectId())
                .depositReturnStatus(e.getDepositReturnStatus()).depositReturnDate(e.getDepositReturnDate())
                .depositReturnEvidenceId(e.getDepositReturnEvidenceId()).transferAmount(e.getTransferAmount())
                .returnedAmount(e.getReturnedAmount()).archiveLocation(e.getArchiveLocation())
                .stageLocked(e.getStageLocked()).notes(e.getNotes()).reviewStatus(e.getReviewStatus())
                .projectSummary(e.getProjectSummary()).rejectionReason(e.getRejectionReason())
                .reviewedBy(e.getReviewedBy()).reviewedAt(e.getReviewedAt())
                .closedAt(e.getClosedAt()).closedBy(e.getClosedBy())
                .createdAt(e.getCreatedAt()).updatedAt(e.getUpdatedAt()).build();
    }

    private void sendClosureSubmitNotification(Long projectId, Long userId) {
        try {
            Project project = projectRepository.findById(projectId).orElse(null);
            if (project == null) return;

            String projectName = project.getName();
            String submitterName = userRepository.findById(userId)
                    .map(User::getFullName).orElse("");

            List<Long> adminIds = getAdminUserIds();

            notificationService.createNotification(new CreateNotificationRequest(
                    NotificationType.APPROVAL.name(),
                    "Project",
                    projectId,
                    "结项审核：项目提交结项申请 - " + projectName,
                    String.format("项目名称：%s\n提交人：%s\n\n请前往项目结项页面审核。", projectName, submitterName),
                    java.util.Map.of("projectId", String.valueOf(projectId), "projectName", projectName,
                            "targetUrl", "/project/" + projectId + "/closure"),
                    adminIds
            ), userId);
        } catch (RuntimeException e) {
            log.warn("sendClosureSubmitNotification failed for project={}: {}", projectId, e.getMessage());
        }
    }

    private void sendClosureReviewNotification(Long projectId, Long submitterId, boolean approved, String reason, Long reviewerId) {
        try {
            if (submitterId == null) return;

            Project project = projectRepository.findById(projectId).orElse(null);
            if (project == null) return;

            String projectName = project.getName();
            String action = approved ? "通过" : "驳回";
            String reviewerName = userRepository.findById(reviewerId)
                    .map(User::getFullName).orElse("");

            notificationService.createNotification(new CreateNotificationRequest(
                    NotificationType.INFO.name(),
                    "Project",
                    projectId,
                    String.format("结项审核%s - %s", action, projectName),
                    String.format("项目名称：%s\n审核结果：%s\n审核人：%s\n%s",
                            projectName, action, reviewerName,
                            approved ? "项目已结项。" : "驳回原因：" + reason),
                    java.util.Map.of("projectId", String.valueOf(projectId), "projectName", projectName,
                            "approved", String.valueOf(approved),
                            "targetUrl", "/project/" + projectId + "/closure"),
                    List.of(submitterId)
            ), reviewerId);
        } catch (RuntimeException e) {
            log.warn("sendClosureReviewNotification failed for project={}: {}", projectId, e.getMessage());
        }
    }

    private List<Long> getAdminUserIds() {
        return userRepository.findEnabledByRoleProfileCodes(List.of("admin", "bid_admin", "bid_lead"))
                .stream().map(User::getId).collect(java.util.stream.Collectors.toList());
    }
}
