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
                user.getRoleCode(),
                user.getRoleName()
        );
    }
}
