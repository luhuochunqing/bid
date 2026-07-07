// Input: ProjectRepository, UserRepository, TenderRepository, ProjectInitiationDetailsRepository, TenderAssignmentRecordRepository, ProjectTransferNotifier
// Output: transfer(projectId, newOwnerId, operatorId, reason) — 项目转移编排
// Pos: project/service/ - 应用服务/命令编排
// 维护声明: 仅维护转移编排；角色校验下沉到 ProjectTransferRolePolicy；通知下沉到 ProjectTransferNotifier。
// 角色校验使用 DbRoleSnapshotResolver（数据库 role_profile 快照），与 /api/users/search 的 UserSearchService 对齐，
// 而非 EffectiveRoleResolver（OSS 登录缓存）。原因：UserPicker 下拉人选人走的是事件库接口，用户角色以事件库同步到
// DB 的 role_profile 为准；后端若改用 OSS 登录缓存校验，会与前端展示来源脱节，出现"前端能选、后端拒绝"的断层。

package com.xiyu.bid.project.service;

import com.xiyu.bid.batch.entity.TenderAssignmentRecord;
import com.xiyu.bid.batch.repository.TenderAssignmentRecordRepository;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.exception.ResourceNotFoundException;
import com.xiyu.bid.project.core.ProjectTransferRolePolicy;
import com.xiyu.bid.project.dto.ProjectTransferResponse;
import com.xiyu.bid.project.entity.ProjectInitiationDetails;
import com.xiyu.bid.project.repository.ProjectInitiationDetailsRepository;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.user.core.DbRoleSnapshotResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 项目转移服务。
 * <p>
 * 投标管理员/组长可将任何状态的项目转移给新负责人。同步更新：
 * <ul>
 *   <li>{@code projects.manager_id}</li>
 *   <li>{@code project_initiation_details.owner_user_id} + {@code project_leader_name}（若存在）</li>
 *   <li>{@code tenders.project_manager_id} + {@code project_manager_name}（若 project.tender_id 存在）</li>
 * </ul>
 * 写入审计日志（{@link TenderAssignmentRecord}，type=TRANSFER）。
 * 给新负责人发站内通知（独立事务，失败不影响主转移）。
 * </p>
 * <p>
 * 旧负责人通过 {@code ProjectAccessScopeService} 实时计算自然失去访问权限
 * （无应用层缓存，下次请求即生效）。
 * </p>
 * <p>
 * 新负责人角色校验使用 {@link DbRoleSnapshotResolver#resolveRoleCode(User)}（数据库 {@code role_profile} 快照），
 * 与 {@code UserSearchService} 返回给 UserPicker 的角色来源一致（事件库同步到 DB 的角色快照），
 * 避免 OSS 登录缓存与选人数据源不一致导致误判。
 * </p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ProjectTransferService {

    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final TenderRepository tenderRepository;
    private final ProjectInitiationDetailsRepository initiationDetailsRepository;
    private final TenderAssignmentRecordRepository assignmentRecordRepository;
    private final ProjectTransferNotifier notifier;

    /**
     * 执行项目转移。
     *
     * @param projectId     项目 ID
     * @param newOwnerId    新负责人用户 ID
     * @param operatorId    操作人用户 ID
     * @param reason        转移原因（可选）
     * @return 转移结果
     * @throws ResourceNotFoundException   如果项目或新负责人不存在
     * @throws IllegalArgumentException    如果新=旧 manager、新=旧 ownerUserId、新负责人停用、角色不允许
     */
    public ProjectTransferResponse transfer(Long projectId, Long newOwnerId, Long operatorId, String reason) {
        // 1. 加载项目
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", projectId.toString()));

        Long oldOwnerId = project.getManagerId();
        String projectName = project.getName();

        // 2. 加载新负责人并校验
        User newOwner = userRepository.findById(newOwnerId)
                .orElseThrow(() -> new ResourceNotFoundException("User", newOwnerId.toString()));

        if (Boolean.FALSE.equals(newOwner.getEnabled())) {
            throw new IllegalArgumentException("新负责人账号已停用");
        }

        // 3. FR-008: 校验新负责人 != 当前 manager
        if (oldOwnerId != null && oldOwnerId.equals(newOwnerId)) {
            throw new IllegalArgumentException("新负责人与当前负责人相同，无需转移");
        }

        // 4. 提前加载 initiationDetails，用于 ownerUserId 校验和后续更新
        Optional<ProjectInitiationDetails> detailsOpt = initiationDetailsRepository.findByProjectId(projectId);
        Long oldOwnerUserId = detailsOpt.map(ProjectInitiationDetails::getOwnerUserId).orElse(null);

        // 4.1 校验新负责人 != 当前 initiationDetails.owner_user_id（避免 owner 与 manager 不一致时产生无效转移）
        if (oldOwnerUserId != null && oldOwnerUserId.equals(newOwnerId)) {
            throw new IllegalArgumentException("新负责人与当前项目负责人相同，无需转移");
        }

        // 4.2 FR-004: 校验新负责人角色。
        // UserPicker 已按事件库同步到 DB 的 role_profile 展示候选，后端角色校验必须与该数据源对齐，
        // 因此使用 DbRoleSnapshotResolver（读 DB 快照）而非 EffectiveRoleResolver（OSS 登录缓存），
        // 避免登录缓存与选人数据源不一致导致"能选不能转"。
        String newOwnerRoleCode = DbRoleSnapshotResolver.resolveRoleCode(newOwner);
        if (!ProjectTransferRolePolicy.isValidNewOwnerRole(newOwnerRoleCode)) {
            throw new IllegalArgumentException(
                    "新负责人必须是投标项目负责人/组长/管理员，当前角色：" + newOwnerRoleCode);
        }

        // 5. 获取旧负责人姓名（用于审计和通知）
        String oldOwnerName = resolveUserName(oldOwnerId);

        // 6. 更新 project.manager_id
        project.setManagerId(newOwnerId);
        projectRepository.save(project);

        // 7. 更新 initiationDetails（若存在）
        detailsOpt.ifPresent(details -> {
            details.setOwnerUserId(newOwnerId);
            details.setProjectLeaderName(newOwner.getFullName());
            // CO-537: 同步回填项目负责人部门
            details.setLeaderDepartment(newOwner.getDepartmentName());
            initiationDetailsRepository.save(details);
        });

        // 8. 获取操作人姓名（审计日志与通知复用，避免重复查询）
        String operatorName = resolveUserName(operatorId);

        // 9. 更新 tender（若 project.tender_id 存在）
        boolean tenderUpdated = false;
        Long tenderId = project.getTenderId();
        if (tenderId != null) {
            Optional<Tender> tenderOpt = tenderRepository.findById(tenderId);
            if (tenderOpt.isPresent()) {
                Tender tender = tenderOpt.get();
                tender.setProjectManagerId(newOwnerId);
                tender.setProjectManagerName(newOwner.getFullName());
                // CO-537: 同步回填标讯"项目部门"
                tender.setDepartment(newOwner.getDepartmentName());
                tenderRepository.save(tender);
                tenderUpdated = true;

                // 10. 写审计日志（复用 TenderAssignmentRecord，type=TRANSFER）
                writeAuditRecord(tenderId, newOwnerId, newOwner.getFullName(),
                        oldOwnerId, oldOwnerName, operatorId, operatorName, reason);
            }
        }

        // 10. 通知新负责人（独立事务，失败不影响主转移）
        try {
            notifier.notifyTransferred(projectId, projectName, newOwnerId, newOwner.getFullName(),
                    oldOwnerName, operatorId, operatorName);
        } catch (RuntimeException e) {
            log.warn("Project transfer notifier threw (should have been caught inside): project {}: {}",
                    projectId, e.getMessage());
        }

        log.info("Project {} transferred from {} (id={}) to {} (id={}) by operator {} (reason: {})",
                projectId, oldOwnerName, oldOwnerId, newOwner.getFullName(), newOwnerId, operatorId, reason);

        // 11. 返回响应
        return ProjectTransferResponse.builder()
                .projectId(projectId)
                .projectName(projectName)
                .oldOwnerUserId(oldOwnerId)
                .oldOwnerName(oldOwnerName)
                .newOwnerUserId(newOwnerId)
                .newOwnerName(newOwner.getFullName())
                .transferredAt(LocalDateTime.now())
                .tenderUpdated(tenderUpdated)
                .tenderId(tenderId)
                .build();
    }

    private void writeAuditRecord(Long tenderId, Long newOwnerId, String newOwnerName,
                                   Long oldOwnerId, String oldOwnerName,
                                   Long operatorId, String operatorName, String reason) {
        String remark = "项目转移: " + (oldOwnerName != null ? oldOwnerName : "无")
                + " → " + newOwnerName
                + (reason != null && !reason.isBlank() ? "（原因：" + reason + "）" : "");
        TenderAssignmentRecord record = TenderAssignmentRecord.builder()
                .tenderId(tenderId)
                .assigneeId(newOwnerId)
                .assigneeName(newOwnerName)
                .assignedById(operatorId)
                .assignedByName(operatorName)
                .type(TenderAssignmentRecord.AssignmentType.TRANSFER)
                .remark(remark)
                .assignedAt(LocalDateTime.now())
                .build();
        assignmentRecordRepository.save(record);
    }

    private String resolveUserName(Long userId) {
        if (userId == null) {
            return null;
        }
        return userRepository.findById(userId)
                .map(User::getFullName)
                .orElse(null);
    }
}
