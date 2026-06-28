package com.xiyu.bid.batch.core;

import com.xiyu.bid.entity.User;

/**
 * 任务批量分配快照
 */
public record BatchAssignmentSnapshot(
        Long assigneeId,
        String assigneeDeptCode,
        String assigneeDeptName,
        String assigneeRoleCode,
        String assigneeRoleName
) {
    public static BatchAssignmentSnapshot fromUser(User user) {
        return new BatchAssignmentSnapshot(
                user.getId(),
                user.getDepartmentCode(),
                user.getDepartmentName(),
                // SAFE: 批量分配快照落库字段，用于事后审计"分配时点的角色"，
                // 应当反映 DB 当时的快照而非 OSS 缓存当下值。CO-373 治理范围外。
                user.getRoleCode(),
                user.getRoleName()
        );
    }
}
