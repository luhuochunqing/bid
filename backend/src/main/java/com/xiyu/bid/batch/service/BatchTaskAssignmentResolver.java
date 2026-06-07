// Input: batch assignment request and current user
// Output: normalized assignment snapshot for batch task updates
// Pos: Service/业务支撑层
// 维护声明: 仅维护批量任务分配目标解析；任务读写和批量响应留在 BatchTaskCommandService。
package com.xiyu.bid.batch.service;

import com.xiyu.bid.batch.core.BatchAssignmentPolicy;
import com.xiyu.bid.batch.core.BatchAssignmentSnapshot;
import com.xiyu.bid.batch.dto.BatchAssignRequest;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.task.dto.TaskAssignmentRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
class BatchTaskAssignmentResolver {

    private final UserRepository userRepository;
    private final BatchAssignmentPolicy assignmentPolicy;

    BatchAssignmentSnapshot resolve(BatchAssignRequest request, User currentUser) {
        TaskAssignmentRequest assignmentRequest = TaskAssignmentRequest.builder()
                .assigneeId(request.getAssigneeId())
                .assigneeDeptCode(request.getAssigneeDeptCode())
                .assigneeDeptName(request.getAssigneeDeptName())
                .assigneeRoleCode(request.getAssigneeRoleCode())
                .assigneeRoleName(request.getAssigneeRoleName())
                .allowCrossDeptCollaboration(Boolean.TRUE.equals(request.getAllowCrossDeptCollaboration()))
                .remark(request.getRemark())
                .build();

        if (assignmentRequest.getAssigneeId() != null) {
            User assignee = userRepository.findById(assignmentRequest.getAssigneeId())
                    .orElseThrow(() -> new IllegalArgumentException("Assignee not found"));
            return assignmentPolicy.resolveUserAssignment(
                    assignee,
                    currentUser,
                    Boolean.TRUE.equals(assignmentRequest.getAllowCrossDeptCollaboration())
            );
        }
        return assignmentPolicy.resolveDepartmentAssignment(assignmentRequest, currentUser);
    }
}
