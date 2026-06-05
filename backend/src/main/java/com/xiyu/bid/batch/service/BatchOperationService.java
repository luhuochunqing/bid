// Input: batch command services and request DTOs
// Output: Batch operation facade methods; audit logging is delegated to command services
// Pos: Service/业务层
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.batch.service;

import com.xiyu.bid.batch.core.BatchValidationPolicy;
import com.xiyu.bid.batch.dto.BatchApproveFeesRequest;
import com.xiyu.bid.batch.dto.BatchAssignRequest;
import com.xiyu.bid.batch.dto.BatchDeleteRequest;
import com.xiyu.bid.batch.dto.BatchOperationResponse;
import com.xiyu.bid.batch.dto.BatchProjectUpdateRequest;
import com.xiyu.bid.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

/**
 * 批处理 facade，保留现有 API，对内委托按资源拆分的命令服务
 */
@Service
@RequiredArgsConstructor
public class BatchOperationService {

    private final BatchTenderCommandService tenderCommandService;
    private final BatchTaskCommandService taskCommandService;
    private final BatchProjectCommandService projectCommandService;
    private final BatchFeeCommandService feeCommandService;
    private final BatchValidationPolicy validationPolicy;

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    @PreAuthorize("hasAuthority('CLAIM_TENDER')")
    public BatchOperationResponse batchClaimTenders(List<Long> tenderIds, Long userId) {
        return tenderCommandService.batchClaimTenders(tenderIds, userId);
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    @PreAuthorize("hasAuthority('ASSIGN_TASK')")
    public BatchOperationResponse batchAssignTasks(List<Long> taskIds, Long assigneeId) {
        return taskCommandService.batchAssignTasks(taskIds, assigneeId);
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    @PreAuthorize("hasAuthority('ASSIGN_TASK')")
    public BatchOperationResponse batchAssignTasks(BatchAssignRequest request, User currentUser) {
        return taskCommandService.batchAssignTasks(request, currentUser);
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    @PreAuthorize("hasAuthority('DELETE_PROJECT')")
    public BatchOperationResponse batchDeleteProjects(List<Long> projectIds, Long userId, User.Role userRole) {
        return projectCommandService.batchDeleteProjects(projectIds, userId, userRole);
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    @PreAuthorize("hasAuthority('DELETE_PROJECT')")
    public BatchOperationResponse batchDeleteProjects(BatchDeleteRequest request) {
        validationPolicy.requireNonNull(request, "Batch delete request cannot be null");
        throw new IllegalStateException("Use the authenticated batchDeleteProjects overload with the current user context");
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public BatchOperationResponse batchDeleteItems(String itemType, List<Long> ids, Long userId, User.Role userRole) {
        if (itemType == null || itemType.trim().isEmpty()) {
            throw new IllegalArgumentException("Item type cannot be null or empty");
        }
        validationPolicy.validateBatchInput(ids, "Item IDs");
        validationPolicy.validateUserId(userId);
        validationPolicy.validateUserRole(userRole);

        if (itemType.trim().equalsIgnoreCase("TENDER") && userRole != User.Role.ADMIN) {
            throw new org.springframework.security.access.AccessDeniedException("Only ADMIN is allowed to delete tenders");
        }

        return switch (itemType.trim().toUpperCase(Locale.ROOT)) {
            case "TENDER" -> tenderCommandService.batchDeleteTenders(ids, userId);
            case "TASK" -> taskCommandService.batchDeleteTasks(ids, userId);
            case "PROJECT" -> projectCommandService.batchDeleteProjects(ids, userId, userRole);
            default -> throw new IllegalArgumentException("Unsupported item type: " + itemType);
        };
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    @PreAuthorize("hasAuthority('UPDATE_PROJECT')")
    public BatchOperationResponse batchUpdateProjects(BatchProjectUpdateRequest request, Long userId, User.Role userRole) {
        return projectCommandService.batchUpdateProjects(request, userId, userRole);
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    @PreAuthorize("hasAuthority('PAY_FEE')")
    public BatchOperationResponse batchApproveFees(BatchApproveFeesRequest request, Long userId) {
        return feeCommandService.batchApproveFees(request, userId);
    }
}
