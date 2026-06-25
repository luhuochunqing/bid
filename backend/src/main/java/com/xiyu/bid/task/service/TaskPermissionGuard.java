package com.xiyu.bid.task.service;

import com.xiyu.bid.common.domain.AuthorizationDecision;
import com.xiyu.bid.entity.Task;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.project.repository.ProjectLeadAssignmentRepository;
import com.xiyu.bid.security.CurrentUserResolver;
import com.xiyu.bid.task.core.TaskOperationPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
class TaskPermissionGuard {

    private final CurrentUserResolver currentUserResolver;
    private final ProjectLeadAssignmentRepository projectLeadAssignmentRepository;

    void assertCanManageTask(Long projectId) {
        User currentUser = currentUserResolver.requireCurrentUser();
        Long[] leadIds = resolveProjectLeadIds(projectId);
        AuthorizationDecision decision = TaskOperationPolicy.canManageTask(
                currentUser.getRoleCode(),
                currentUser.getId(),
                leadIds[0],
                leadIds[1]
        );
        if (!decision.allowed()) {
            throw new AccessDeniedException(decision.reason());
        }
    }

    void assertCanManageOrSubmitTask(Task task) {
        User currentUser = currentUserResolver.requireCurrentUser();
        Long[] leadIds = resolveProjectLeadIds(task.getProjectId());
        AuthorizationDecision manageDecision = TaskOperationPolicy.canManageTask(
                currentUser.getRoleCode(),
                currentUser.getId(),
                leadIds[0],
                leadIds[1]
        );
        if (manageDecision.allowed()) {
            return;
        }
        AuthorizationDecision submitDecision = TaskOperationPolicy.canActAsAssignee(
                task.getAssigneeId(),
                currentUser.getId()
        );
        if (!submitDecision.allowed()) {
            throw new AccessDeniedException(submitDecision.reason());
        }
    }

    void assertCanTransitionTaskStatus(Task task, Task.Status targetStatus) {
        User currentUser = currentUserResolver.requireCurrentUser();
        Long[] leadIds = resolveProjectLeadIds(task.getProjectId());
        if (targetStatus == Task.Status.REVIEW) {
            AuthorizationDecision decision = TaskOperationPolicy.canActAsAssignee(
                    task.getAssigneeId(),
                    currentUser.getId()
            );
            if (!decision.allowed()) {
                throw new AccessDeniedException(decision.reason());
            }
        } else if (targetStatus == Task.Status.COMPLETED || targetStatus == Task.Status.TODO) {
            AuthorizationDecision decision = TaskOperationPolicy.canReviewTask(
                    currentUser.getRoleCode(),
                    currentUser.getId(),
                    leadIds[0],
                    leadIds[1],
                    task.getAssigneeId()
            );
            if (!decision.allowed()) {
                throw new AccessDeniedException(decision.reason());
            }
        }
    }

    private Long[] resolveProjectLeadIds(Long projectId) {
        return projectLeadAssignmentRepository.resolveLeadIdsByProjectId(projectId);
    }
}
