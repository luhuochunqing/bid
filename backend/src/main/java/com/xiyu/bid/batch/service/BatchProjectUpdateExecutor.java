package com.xiyu.bid.batch.service;

import com.xiyu.bid.batch.dto.BatchOperationResponse;
import com.xiyu.bid.batch.dto.BatchProjectUpdateRequest;
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
class BatchProjectUpdateExecutor {

    private static final int MAX_BATCH_SIZE = 100;

    private final ProjectRepository projectRepository;
    private final BatchOperationLogService batchOperationLogService;

    BatchOperationResponse execute(BatchProjectUpdateRequest request, Long userId, User.Role userRole) {
        if (request == null) {
            throw new IllegalArgumentException("Batch update request cannot be null");
        }
        if (!request.hasUpdates()) {
            throw new IllegalArgumentException("At least one field (status or managerId) must be specified for update");
        }
        BatchValidationPolicy.validateBatchInput(request.getProjectIds(), "Project IDs", MAX_BATCH_SIZE);
        BatchValidationPolicy.validateUserId(userId);
        BatchValidationPolicy.validateUserRole(userRole);

        BatchOperationResponse response = BatchOperationResponse.builder()
                .operationType("UPDATE")
                .operationTime(LocalDateTime.now())
                .build();
        response.setTotalCount(request.getProjectIds().size());

        List<Project> projectsToUpdate = new ArrayList<>();
        for (Long projectId : request.getProjectIds()) {
            try {
                var projectOpt = projectRepository.findById(projectId);
                if (projectOpt.isEmpty()) {
                    response.addError(projectId, "Project not found with ID: " + projectId, "NOT_FOUND");
                    continue;
                }
                Project project = projectOpt.get();
                if (!hasUpdatePermission(project, userId, userRole)) {
                    response.addError(projectId, "Permission denied: you must be the project manager or admin", "PERMISSION_DENIED");
                    continue;
                }
                if (request.getStatus() != null) {
                    project.setStatus(request.getStatus());
                }
                if (request.getManagerId() != null) {
                    project.setManagerId(request.getManagerId());
                }
                projectsToUpdate.add(project);
                response.addSuccess(projectId);
            } catch (RuntimeException exception) {
                response.addError(projectId, "Failed to update project: " + exception.getMessage(), "UPDATE_ERROR");
            }
        }

        if (!projectsToUpdate.isEmpty()) {
            projectRepository.saveAll(projectsToUpdate);
        }
        response.setSuccess(response.getFailureCount() == 0);
        batchOperationLogService.record(response, "PROJECT", "UPDATE", userId);
        return response;
    }

    private boolean hasUpdatePermission(Project project, Long userId, User.Role userRole) {
        return userRole == User.Role.ADMIN || project.getManagerId().equals(userId);
    }
}
