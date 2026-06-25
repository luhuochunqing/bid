package com.xiyu.bid.task.service;

import com.xiyu.bid.entity.Task;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.project.repository.ProjectLeadAssignmentRepository;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.task.core.TaskOperationDecision;
import com.xiyu.bid.task.core.TaskOperationPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
class TaskPermissionGuard {

    private final UserRepository userRepository;
    private final ProjectLeadAssignmentRepository projectLeadAssignmentRepository;

    void assertCanManageTask(Long projectId) {
        User currentUser = resolveCurrentUser();
        if (currentUser == null) {
            throw new AccessDeniedException("无法识别当前用户");
        }
        Long[] leadIds = resolveProjectLeadIds(projectId);
        TaskOperationDecision decision = TaskOperationPolicy.canManageTask(
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
        User currentUser = resolveCurrentUser();
        if (currentUser == null) {
            throw new AccessDeniedException("无法识别当前用户");
        }
        Long[] leadIds = resolveProjectLeadIds(task.getProjectId());
        TaskOperationDecision manageDecision = TaskOperationPolicy.canManageTask(
                currentUser.getRoleCode(),
                currentUser.getId(),
                leadIds[0],
                leadIds[1]
        );
        if (manageDecision.allowed()) {
            return;
        }
        TaskOperationDecision submitDecision = TaskOperationPolicy.canSubmitTask(
                currentUser.getRoleCode(),
                task.getAssigneeId(),
                currentUser.getId()
        );
        if (!submitDecision.allowed()) {
            throw new AccessDeniedException(submitDecision.reason());
        }
    }

    void assertCanTransitionTaskStatus(Task task, Task.Status targetStatus) {
        User currentUser = resolveCurrentUser();
        if (currentUser == null) {
            throw new AccessDeniedException("无法识别当前用户");
        }
        Long[] leadIds = resolveProjectLeadIds(task.getProjectId());
        if (targetStatus == Task.Status.REVIEW) {
            TaskOperationDecision decision = TaskOperationPolicy.canSubmitTask(
                    currentUser.getRoleCode(),
                    task.getAssigneeId(),
                    currentUser.getId()
            );
            if (!decision.allowed()) {
                throw new AccessDeniedException(decision.reason());
            }
        } else if (targetStatus == Task.Status.COMPLETED || targetStatus == Task.Status.TODO) {
            TaskOperationDecision decision = TaskOperationPolicy.canReviewTask(
                    currentUser.getRoleCode(),
                    currentUser.getId(),
                    leadIds[0],
                    leadIds[1]
            );
            if (!decision.allowed()) {
                throw new AccessDeniedException(decision.reason());
            }
        }
    }

    private Long[] resolveProjectLeadIds(Long projectId) {
        return projectLeadAssignmentRepository.findByProjectId(projectId)
                .map(a -> new Long[]{a.getPrimaryLeadUserId(), a.getSecondaryLeadUserId()})
                .orElse(new Long[]{null, null});
    }

    private User resolveCurrentUser() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }
        return userRepository.findByUsername(auth.getName()).orElse(null);
    }
}
