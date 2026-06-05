package com.xiyu.bid.batch.service;

import com.xiyu.bid.batch.dto.BatchOperationResponse;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
class BatchProjectDeleteExecutor {

    private static final int MAX_BATCH_SIZE = 100;

    private final ProjectRepository projectRepository;
    private final BatchOperationLogService batchOperationLogService;

    BatchOperationResponse execute(List<Long> projectIds, Long userId, User.Role userRole) {
        BatchValidationPolicy.validateBatchInput(projectIds, "Project IDs", MAX_BATCH_SIZE);
        BatchValidationPolicy.validateUserId(userId);
        BatchValidationPolicy.validateUserRole(userRole);

        BatchOperationResponse response = BatchOperationResponse.builder()
                .operationType("DELETE")
                .operationTime(LocalDateTime.now())
                .build();
        response.setTotalCount(projectIds.size());

        List<Project> projectsToDelete = new ArrayList<>();
        for (Long projectId : projectIds) {
            try {
                var projectOpt = projectRepository.findById(projectId);
                if (projectOpt.isEmpty()) {
                    response.addError(projectId, "Project not found with ID: " + projectId, "NOT_FOUND");
                    continue;
                }
                Project project = projectOpt.get();
                if (!hasDeletePermission(project, userId, userRole)) {
                    response.addError(projectId, "Permission denied: you must be the project manager or admin", "PERMISSION_DENIED");
                    continue;
                }
                projectsToDelete.add(project);
                response.addSuccess(projectId);
            } catch (RuntimeException exception) {
                response.addError(projectId, "Failed to delete project: " + exception.getMessage(), "DELETE_ERROR");
            }
        }

        if (!projectsToDelete.isEmpty()) {
            projectRepository.deleteAll(projectsToDelete);
        }
        response.setSuccess(response.getFailureCount() == 0);
        batchOperationLogService.record(response, "PROJECT", "DELETE", userId);
        return response;
    }

    private boolean hasDeletePermission(Project project, Long userId, User.Role userRole) {
        return userRole == User.Role.ADMIN || project.getManagerId().equals(userId);
    }
}
