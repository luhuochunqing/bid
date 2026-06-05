package com.xiyu.bid.task.service;

import com.xiyu.bid.task.core.DeliverableAssociationPolicy;
import com.xiyu.bid.task.core.TaskTransitionPolicy;
import com.xiyu.bid.task.dto.DeliverableCoverageDTO;
import com.xiyu.bid.task.dto.TaskDeliverableAssembler;
import com.xiyu.bid.task.dto.TaskDeliverableCreateRequest;
import com.xiyu.bid.task.dto.TaskDeliverableDTO;
import com.xiyu.bid.task.entity.TaskDeliverable;
import com.xiyu.bid.entity.Task;
import com.xiyu.bid.repository.TaskRepository;
import com.xiyu.bid.task.repository.TaskDeliverableRepository;
import com.xiyu.bid.util.InputSanitizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Application service for task deliverable CRUD.
 * Orchestrates: load → validate via pure core → persist → return DTO.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskDeliverableService {

    private final TaskRepository taskRepository;
    private final TaskDeliverableRepository taskDeliverableRepository;

    @Transactional
    public TaskDeliverableDTO createDeliverable(
            Long projectId,
            Long taskId,
            TaskDeliverableCreateRequest request,
            String username) {

        // 1. Load and verify task ownership
        var task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("任务不存在: " + taskId));
        if (!task.getProjectId().equals(projectId)) {
            throw new IllegalArgumentException("任务不属于该项目");
        }

        // 2. Validate association rules
        int existingCount = (int) taskDeliverableRepository.countByTaskId(taskId);
        var validation = DeliverableAssociationPolicy.validateAssociation(
                task.getStatus().name(),
                toCoreType(parseType(request.getDeliverableType())),
                existingCount);
        if (!validation.valid()) {
            throw new IllegalArgumentException(validation.rejectionReason());
        }

        // 3. Determine next version
        int nextVersion = existingCount + 1;

        // 4. Build and save entity
        String sanitizedName = InputSanitizer.sanitizeString(request.getName(), 255);
        String sanitizedType = InputSanitizer.sanitizeString(request.getDeliverableType() != null ? request.getDeliverableType() : "DOCUMENT", 30);
        var sanitizedRequest = TaskDeliverableCreateRequest.builder()
                .name(sanitizedName)
                .deliverableType(sanitizedType)
                .size(request.getSize() != null ? InputSanitizer.sanitizeString(request.getSize(), 50) : null)
                .fileType(request.getFileType() != null ? InputSanitizer.sanitizeString(request.getFileType(), 100) : null)
                .url(request.getUrl() != null ? InputSanitizer.sanitizeString(request.getUrl(), 500) : null)
                .build();

        var entity = TaskDeliverableAssembler.toEntity(
                sanitizedRequest, taskId, nextVersion, null, username);
        entity = taskDeliverableRepository.save(entity);

        log.info("Created deliverable '{}' for task {} by {}", sanitizedName, taskId, username);

        // 5. Auto-status suggestion on first deliverable upload
        var suggestedStatus = TaskTransitionPolicy.computeAutoStatusOnDeliverable(
                TaskTransitionPolicy.TaskStatus.valueOf(task.getStatus().name()),
                existingCount);
        if (suggestedStatus == TaskTransitionPolicy.TaskStatus.IN_PROGRESS
                && task.getStatus() == Task.Status.TODO) {
            task.setStatus(Task.Status.IN_PROGRESS);
            taskRepository.save(task);
            log.info("Auto-transitioned task {} to IN_PROGRESS after deliverable upload", taskId);
        }

        return TaskDeliverableAssembler.toDTO(entity);
    }

    @Transactional(readOnly = true)
    public List<TaskDeliverableDTO> getDeliverablesByTaskId(Long projectId, Long taskId) {
        // Verify task belongs to project
        var task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("任务不存在: " + taskId));
        if (!task.getProjectId().equals(projectId)) {
            throw new IllegalArgumentException("任务不属于该项目");
        }
        return taskDeliverableRepository.findByTaskIdOrderByCreatedAtDesc(taskId).stream()
                .map(TaskDeliverableAssembler::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public void deleteDeliverable(Long projectId, Long taskId, Long deliverableId) {
        var task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("任务不存在: " + taskId));
        if (!task.getProjectId().equals(projectId)) {
            throw new IllegalArgumentException("任务不属于该项目");
        }
        var deliverable = taskDeliverableRepository.findById(deliverableId)
                .orElseThrow(() -> new IllegalArgumentException("交付物不存在: " + deliverableId));
        if (!deliverable.getTaskId().equals(taskId)) {
            throw new IllegalArgumentException("交付物不属于该任务");
        }
        taskDeliverableRepository.delete(deliverable);
        log.info("Deleted deliverable {} from task {} by {}", deliverableId, taskId,
                deliverable.getUploaderName());
    }

    @Transactional(readOnly = true)
    public DeliverableCoverageDTO getDeliverableCoverage(Long taskId, String suggestedTypesJson) {
        var allTypes = taskDeliverableRepository.findByTaskIdOrderByCreatedAtDesc(taskId);

        List<DeliverableAssociationPolicy.DeliverableType> actualEnums = allTypes.stream()
                .map(d -> toCoreType(d.getDeliverableType()))
                .collect(Collectors.toList());

        // Parse suggested types from score draft JSON if available
        List<String> requiredTypes = List.of();
        if (suggestedTypesJson != null && !suggestedTypesJson.isBlank()) {
            // Simple comma/space-separated list fallback
            requiredTypes = List.of(suggestedTypesJson.split("[,\\s]+"));
        }

        var coverage = DeliverableAssociationPolicy.computeCompletionCoverage(requiredTypes, actualEnums);
        return DeliverableCoverageDTO.builder()
                .taskId(taskId)
                .requiredCount(coverage.required())
                .coveredCount(coverage.covered())
                .percentage(coverage.percentage())
                .typeCoverages(coverage.typeCoverages().stream()
                        .map(tc -> new DeliverableCoverageDTO.TypeCoverage(
                                tc.type(), tc.label(), tc.covered(), tc.count()))
                        .collect(Collectors.toList()))
                .build();
    }

    private static TaskDeliverable.DeliverableType parseType(String value) {
        if (value == null || value.isBlank()) {
            return TaskDeliverable.DeliverableType.DOCUMENT;
        }
        try {
            return TaskDeliverable.DeliverableType.valueOf(
                    value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return TaskDeliverable.DeliverableType.DOCUMENT;
        }
    }

    /** Convert entity DeliverableType to core policy enum. */
    private static DeliverableAssociationPolicy.DeliverableType toCoreType(
            final TaskDeliverable.DeliverableType type) {
        if (type == null) {
            return DeliverableAssociationPolicy.DeliverableType.OTHER;
        }
        return switch (type) {
            case DOCUMENT -> DeliverableAssociationPolicy.DeliverableType.DOCUMENT;
            case QUALIFICATION -> DeliverableAssociationPolicy
                    .DeliverableType.QUALIFICATION;
            case TECHNICAL -> DeliverableAssociationPolicy
                    .DeliverableType.TECHNICAL;
            case QUOTATION -> DeliverableAssociationPolicy
                    .DeliverableType.QUOTATION;
            case OTHER -> DeliverableAssociationPolicy
                    .DeliverableType.OTHER;
        };
    }
}
