// Input: 立项审核请求 (approve/reject) + 当前用户 + 数据权限校验
// Output: 通过 InitiationReviewPolicy + ProjectAccessScopeService 校验 + 持久化 + 阶段流转
// Pos: project/service/ - 编排层
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.project.service;

import com.xiyu.bid.annotation.Auditable;
import com.xiyu.bid.exception.ResourceNotFoundException;
import com.xiyu.bid.project.core.InitiationReviewPolicy;
import com.xiyu.bid.project.core.InitiationReviewStatus;
import com.xiyu.bid.project.core.ProjectStage;
import com.xiyu.bid.project.core.ProjectStageTransitionPolicy;
import com.xiyu.bid.project.dto.InitiationApprovalRequest;
import com.xiyu.bid.project.dto.InitiationRejectionRequest;
import com.xiyu.bid.project.entity.ProjectInitiationDetails;
import com.xiyu.bid.project.entity.ProjectLeadAssignment;
import com.xiyu.bid.project.repository.ProjectInitiationDetailsRepository;
import com.xiyu.bid.project.repository.ProjectLeadAssignmentRepository;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.service.ProjectAccessScopeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

/**
 * 立项审核服务。产品蓝图 V1.1 §4.3。
 * <p>
 * 职责：
 * <ul>
 *   <li>{@link #approve}：审核通过 + 分配团队 + 推进 INITIATED→DRAFTING + 锁定字段（一步完成）</li>
 *   <li>{@link #reject}：记录驳回原因，等待项目负责人修改后重新提交</li>
 * </ul>
 * 数据权限：所有写操作前调用 {@link ProjectAccessScopeService#assertCurrentUserCanAccessProject}。
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ProjectInitiationApprovalService {

    private final ProjectInitiationDetailsRepository initiationRepo;
    private final ProjectLeadAssignmentRepository leadRepo;
    private final ProjectStageService projectStageService;
    private final ProjectAccessScopeService projectAccessScopeService;
    private final UserRepository userRepository;

    /**
     * 审核通过立项申请，原子完成：状态变更 + 团队分配 + 阶段推进 + 字段锁定。
     *
     * @param projectId 项目 ID
     * @param req 审核请求（必须包含 primaryLeadUserId）
     * @param currentUserId 当前审核人 ID（写入 reviewedBy）
     * @throws ResponseStatusException 422 验证失败 / 404 立项详情不存在
     * @throws org.springframework.security.access.AccessDeniedException 数据权限不足
     */
    @Auditable(action = "APPROVE_INITIATION", entityType = "ProjectInitiationDetails",
            description = "审核通过项目立项: 分配团队 + 推进阶段 + 锁定字段")
    public void approve(Long projectId, InitiationApprovalRequest req, Long currentUserId) {
        projectAccessScopeService.assertCurrentUserCanAccessProject(projectId);
        ProjectInitiationDetails entity = mustGet(projectId);
        InitiationReviewStatus current = parseStatus(entity.getReviewStatus());
        var decision = InitiationReviewPolicy.validateApproval(current, req);
        if (!decision.allowed()) {
            var deny = (InitiationReviewPolicy.Decision.Deny) decision;
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, deny.reason());
        }

        // 1. 记录审核通过
        entity.setReviewStatus(InitiationReviewStatus.APPROVED.name());
        entity.setReviewedBy(currentUserId);
        entity.setReviewedAt(LocalDateTime.now());
        entity.setLocked(Boolean.TRUE); // 锁定字段
        entity.setUpdatedBy(currentUserId);
        initiationRepo.save(entity);

        // 2. 分配团队
        ProjectLeadAssignment assignment = leadRepo.findByProjectId(projectId)
                .orElseGet(() -> ProjectLeadAssignment.builder()
                        .projectId(projectId)
                        .build());
        assignment.setPrimaryLeadUserId(req.getPrimaryLeadUserId());
        assignment.setSecondaryLeadUserId(req.getSecondaryLeadUserId());
        assignment.setAssignedAt(LocalDateTime.now());
        assignment.setAssignedBy(currentUserId);
        leadRepo.save(assignment);

        // 2a. 同步投标负责人姓名到项目详情
        if (req.getPrimaryLeadUserId() != null) {
            userRepository.findById(req.getPrimaryLeadUserId())
                    .ifPresent(u -> entity.setBiddingLeaderName(u.getFullName()));
            initiationRepo.save(entity);
        }

        // 3. 推进项目阶段到 DRAFTING（如果当前是 INITIATED）
        ProjectStage stage = projectStageService.currentStage(projectId);
        if (stage == ProjectStage.INITIATED) {
            projectStageService.requestTransition(projectId, ProjectStage.DRAFTING,
                    ProjectStageTransitionPolicy.GateInputs.EMPTY);
        }

        log.info("Initiation approved project={} primaryLead={} reviewer={}",
                projectId, req.getPrimaryLeadUserId(), currentUserId);
    }

    /**
     * 驳回立项申请，记录原因供项目负责人修改后重新提交。
     *
     * @param projectId 项目 ID
     * @param req 驳回请求（必须包含非空 rejectionReason）
     * @param currentUserId 当前审核人 ID
     * @throws ResponseStatusException 422 验证失败 / 404 立项详情不存在
     * @throws org.springframework.security.access.AccessDeniedException 数据权限不足
     */
    @Auditable(action = "REJECT_INITIATION", entityType = "ProjectInitiationDetails",
            description = "驳回项目立项: 记录原因等待重新提交")
    public void reject(Long projectId, InitiationRejectionRequest req, Long currentUserId) {
        projectAccessScopeService.assertCurrentUserCanAccessProject(projectId);
        ProjectInitiationDetails entity = mustGet(projectId);
        InitiationReviewStatus current = parseStatus(entity.getReviewStatus());
        var decision = InitiationReviewPolicy.validateRejection(current, req);
        if (!decision.allowed()) {
            var deny = (InitiationReviewPolicy.Decision.Deny) decision;
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, deny.reason());
        }

        entity.setReviewStatus(InitiationReviewStatus.REJECTED.name());
        entity.setRejectionReason(req.getRejectionReason());
        entity.setReviewedBy(currentUserId);
        entity.setReviewedAt(LocalDateTime.now());
        entity.setUpdatedBy(currentUserId);
        initiationRepo.save(entity);

        log.info("Initiation rejected project={} reviewer={} reason={}",
                projectId, currentUserId, req.getRejectionReason());
    }

    private ProjectInitiationDetails mustGet(Long projectId) {
        return initiationRepo.findByProjectId(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("ProjectInitiationDetails",
                        String.valueOf(projectId)));
    }

    /**
     * 解析数据库中的 reviewStatus 字符串到枚举。
     * <p>不容忍数据异常：未知值抛 500 等待运维介入（而非静默 fallback）。</p>
     */
    private InitiationReviewStatus parseStatus(String raw) {
        if (raw == null) return InitiationReviewStatus.DRAFT;
        try {
            return InitiationReviewStatus.valueOf(raw);
        } catch (IllegalArgumentException ex) {
            log.warn("Unknown reviewStatus in DB: {}", raw);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "立项审核状态数据异常: " + raw);
        }
    }
}
