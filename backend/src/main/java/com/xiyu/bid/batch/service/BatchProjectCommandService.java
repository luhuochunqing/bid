// Input: project repository, validation policy, and log service
// Output: project batch command orchestration
// Pos: Service/业务层
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.batch.service;

import com.xiyu.bid.batch.core.BatchValidationPolicy;
import com.xiyu.bid.batch.dto.BatchOperationResponse;
import com.xiyu.bid.batch.dto.BatchProjectUpdateRequest;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.service.ProjectAccessScopeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 项目批处理命令
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BatchProjectCommandService {

    private final ProjectRepository projectRepository;
    private final BatchValidationPolicy validationPolicy;
    private final BatchOperationLogService logService;
    private final ProjectAccessScopeService projectAccessScopeService;

    public BatchOperationResponse batchDeleteProjects(List<Long> projectIds, Long userId, User.Role userRole) {
        validationPolicy.validateBatchInput(projectIds, "Project IDs");
        validationPolicy.validateUserId(userId);
        validationPolicy.validateUserRole(userRole);
        log.info("Batch deleting projects: count={}, userId={}", projectIds.size(), userId);

        BatchOperationResponse response = newResponse("DELETE", projectIds.size());
        List<Project> projectsToDelete = new ArrayList<>();
        for (Long projectId : projectIds) {
            try {
                var projectOpt = projectRepository.findById(projectId);
                if (projectOpt.isEmpty()) {
                    response.addError(projectId, "Project not found with ID: " + projectId, "NOT_FOUND");
                    continue;
                }
                Project project = projectOpt.get();
                projectAccessScopeService.assertCurrentUserCanAccessProject(projectId);
                if (!hasManagerPermission(project, userId, userRole)) {
                    response.addError(projectId, "Permission denied: you must be the project manager or admin", "PERMISSION_DENIED");
                    continue;
                }
                projectsToDelete.add(project);
                response.addSuccess(projectId);
            } catch (RuntimeException exception) {
                addRuntimeError(response, projectId, exception, "DELETE_ERROR");
            }
        }
        if (!projectsToDelete.isEmpty()) {
            projectRepository.deleteAll(projectsToDelete);
        }
        complete(response, "PROJECT", "DELETE", userId);
        return response;
    }

    public BatchOperationResponse batchUpdateProjects(BatchProjectUpdateRequest request, Long userId, User.Role userRole) {
        validationPolicy.requireNonNull(request, "Batch update request cannot be null");
        if (!request.hasUpdates()) {
            throw new IllegalArgumentException("At least one field (status or managerId) must be specified for update");
        }
        validationPolicy.validateBatchInput(request.getProjectIds(), "Project IDs");
        validationPolicy.validateUserId(userId);
        validationPolicy.validateUserRole(userRole);
        log.info("Batch updating projects: count={}, userId={}, status={}, managerId={}",
                request.getProjectIds().size(), userId, request.getStatus(), request.getManagerId());

        BatchOperationResponse response = newResponse("UPDATE", request.getProjectIds().size());
        List<Project> projectsToUpdate = new ArrayList<>();
        for (Long projectId : request.getProjectIds()) {
            try {
                var projectOpt = projectRepository.findById(projectId);
                if (projectOpt.isEmpty()) {
                    response.addError(projectId, "Project not found with ID: " + projectId, "NOT_FOUND");
                    continue;
                }
                Project project = projectOpt.get();
                projectAccessScopeService.assertCurrentUserCanAccessProject(projectId);
                if (!hasManagerPermission(project, userId, userRole)) {
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
                addRuntimeError(response, projectId, exception, "UPDATE_ERROR");
            }
        }
        if (!projectsToUpdate.isEmpty()) {
            projectRepository.saveAll(projectsToUpdate);
        }
        complete(response, "PROJECT", "UPDATE", userId);
        return response;
    }

    private boolean hasManagerPermission(Project project, Long userId, User.Role userRole) {
        return userRole == User.Role.ADMIN || project.getManagerId().equals(userId);
    }

    private void addRuntimeError(BatchOperationResponse response, Long projectId, RuntimeException exception, String fallbackCode) {
        if (BatchProjectAccessGuard.isAccessDenied(exception)) {
            response.addError(projectId, "Permission denied: project is outside current data scope", "PERMISSION_DENIED");
            return;
        }
        response.addError(projectId, exception.getMessage(), fallbackCode);
    }

    private BatchOperationResponse newResponse(String operationType, int totalCount) {
        BatchOperationResponse response = BatchOperationResponse.builder()
                .operationType(operationType)
                .operationTime(LocalDateTime.now())
                .build();
        response.setTotalCount(totalCount);
        return response;
    }

    private void complete(BatchOperationResponse response, String itemType, String operationType, Long userId) {
        logService.record(response, itemType, operationType, userId);
        response.setSuccess(response.getFailureCount() == 0);
    }
}
