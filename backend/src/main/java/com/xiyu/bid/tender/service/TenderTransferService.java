// Input: TenderRepository, TenderAssignmentRecordRepository, UserRepository, TenderAuditService
// Output: transfer(tenderId, newOwnerId, operatorId) — 转派标讯给新项目负责人
// Pos: Service/标讯转派命令服务
// 维护声明: 仅维护转派业务逻辑；状态校验下沉到 StatusTransitionPolicy；权限校验在 controller 层。

package com.xiyu.bid.tender.service;

import com.xiyu.bid.batch.entity.TenderAssignmentRecord;
import com.xiyu.bid.batch.repository.TenderAssignmentRecordRepository;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.exception.ResourceNotFoundException;
import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.tender.dto.TenderTransferResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 标讯转派服务。
 * <p>
 * 投标管理员/组长可将「跟踪中」或「已评估」状态的标讯转派给新项目负责人。
 * 转派后原负责人立即失去访问权限（通过数据权限即时计算 —— 无需会话刷新）。
 * </p>
 * <p>
 * FR-009 ~ FR-014:
 * <ul>
 *   <li>FR-009: 跟踪中/已评估标讯可转派</li>
 *   <li>FR-010: 仅投标管理员/组长可执行</li>
 *   <li>FR-011: 仅修改项目负责人</li>
 *   <li>FR-012: 项目部门自动更新</li>
 *   <li>FR-013: 原负责人立即失去访问权限</li>
 *   <li>FR-014: 拒绝转派给当前负责人</li>
 * </ul>
 * </p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class TenderTransferService {

    private static final List<Tender.Status> TRANSFERABLE_STATUSES = List.of(
            Tender.Status.TRACKING,
            Tender.Status.EVALUATED
    );

    private final TenderRepository tenderRepository;
    private final UserRepository userRepository;
    private final TenderAssignmentRecordRepository assignmentRecordRepository;
    private final TenderAuditService tenderAuditService;

    /**
     * 执行标讯转派。
     *
     * @param tenderId   标讯 ID
     * @param newOwnerId 新项目负责人用户 ID
     * @param operatorId 操作人用户 ID
     * @return 转派结果
     * @throws IllegalArgumentException 如果状态不允许、转派给当前负责人等
     * @throws ResourceNotFoundException 如果标讯或新负责人不存在
     */
    public TenderTransferResponse transfer(Long tenderId, Long newOwnerId, Long operatorId) {
        // 1. 加载标讯
        Tender tender = tenderRepository.findById(tenderId)
                .orElseThrow(() -> new ResourceNotFoundException("Tender", tenderId.toString()));

        // 防御性校验：实例级权限确认（Controller 已做角色校验）
        if (tender.getProjectManagerId() == null) {
            throw new IllegalArgumentException("标讯尚未分配项目负责人，无法转派");
        }

        // 2. 校验状态：仅在跟踪中和已评估时可转派
        if (!TRANSFERABLE_STATUSES.contains(tender.getStatus())) {
            throw new IllegalArgumentException("标讯状态已变更，无法转派");
        }

        // 3. 校验新负责人存在且启用
        User newOwner = userRepository.findById(newOwnerId)
                .orElseThrow(() -> new ResourceNotFoundException("User", newOwnerId.toString()));
        if (Boolean.FALSE.equals(newOwner.getEnabled())) {
            throw new IllegalArgumentException("新负责人账号已停用，无法转派");
        }

        // 4. 获取原负责人信息
        Long oldOwnerId = tender.getProjectManagerId();
        String oldOwnerName = tender.getProjectManagerName();

        // 5. FR-014: 拒绝转派给当前负责人
        if (oldOwnerId != null && oldOwnerId.equals(newOwnerId)) {
            throw new IllegalArgumentException("不能转派给当前负责人");
        }

        // 6. 更新标讯项目负责人及部门
        tender.setProjectManagerId(newOwnerId);
        tender.setProjectManagerName(newOwner.getFullName());
        tender.setDepartment(newOwner.getDepartmentName());
        tenderRepository.save(tender);

        // 7. 写入 TRANSFER 类型分配记录
        String operatorName = resolveOperatorName(operatorId);
        TenderAssignmentRecord record = TenderAssignmentRecord.builder()
                .tenderId(tenderId)
                .assigneeId(newOwnerId)
                .assigneeName(newOwner.getFullName())
                .assignedById(operatorId)
                .assignedByName(operatorName)
                .type(TenderAssignmentRecord.AssignmentType.TRANSFER)
                .remark("转派: " + (oldOwnerName != null ? oldOwnerName : "无") + " → " + newOwner.getFullName())
                .assignedAt(LocalDateTime.now())
                .build();
        assignmentRecordRepository.save(record);

        // 8. 记录转派审计日志
        tenderAuditService.logTransfer(tenderId, oldOwnerName, newOwner.getFullName(),
                operatorName, String.valueOf(operatorId), "system");

        log.info("Tender {} transferred from {} (id={}) to {} (id={}) by operator {}",
                tenderId, oldOwnerName, oldOwnerId, newOwner.getFullName(), newOwnerId, operatorId);

        // 9. 返回转派结果
        return TenderTransferResponse.builder()
                .tenderId(tenderId)
                .oldOwnerId(oldOwnerId)
                .newOwnerId(newOwnerId)
                .department(newOwner.getDepartmentName())
                .status(tender.getStatus())
                .build();
    }

    private String resolveOperatorName(Long operatorId) {
        return userRepository.findById(operatorId)
                .map(User::getFullName)
                .orElse("未知用户");
    }
}
