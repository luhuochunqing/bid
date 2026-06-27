package com.xiyu.bid.task.service;

import com.xiyu.bid.common.domain.AuthorizationDecision;
import com.xiyu.bid.exception.ResourceNotFoundException;
import com.xiyu.bid.task.core.DeliverableAssociationPolicy;
import com.xiyu.bid.task.core.TaskOperationPolicy;
import com.xiyu.bid.task.core.TaskTransitionPolicy;
import com.xiyu.bid.task.dto.DeliverableCoverageDTO;
import com.xiyu.bid.task.dto.TaskDeliverableAssembler;
import com.xiyu.bid.task.dto.TaskDeliverableCreateRequest;
import com.xiyu.bid.task.dto.TaskDeliverableDTO;
import com.xiyu.bid.task.entity.TaskDeliverable;
import com.xiyu.bid.entity.Task;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.TaskRepository;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.task.repository.TaskDeliverableRepository;
import com.xiyu.bid.util.InputSanitizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
    private final UserRepository userRepository;

    @Value("${app.doc-insight.upload-dir:}")
    private String docInsightUploadDir;

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

        // 1b. 仅任务指派人本人可上传交付物（蓝图 §2.3.1；角色无关）。
        // 内部调用（username 为 null 或 "system"）跳过身份校验。
        if (username != null && !"system".equals(username)) {
            User currentUser = userRepository.findByUsername(username).orElse(null);
            Long currentUserId = currentUser != null ? currentUser.getId() : null;
            AuthorizationDecision decision = TaskOperationPolicy.canActAsAssignee(task.getAssigneeId(), currentUserId);
            if (!decision.allowed()) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, decision.reason());
            }
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

        // 业务规则：上传交付物不改变任务状态，保持 TODO 直到提交审核
        // 不再自动转为 IN_PROGRESS

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

    /**
     * Load deliverable file for download.
     * Validates project-task-deliverable ownership and resolves the doc-insight:// storage path.
     *
     * @param projectId     owning project id
     * @param taskId        owning task id
     * @param deliverableId deliverable id
     * @return file info for download response
     */
    @Transactional(readOnly = true)
    public DeliverableDownloadFile getDeliverableFile(Long projectId, Long taskId, Long deliverableId) {
        var task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("任务不存在: " + taskId));
        if (!task.getProjectId().equals(projectId)) {
            throw new IllegalArgumentException("任务不属于该项目");
        }
        var deliverable = taskDeliverableRepository.findById(deliverableId)
                .orElseThrow(() -> ResourceNotFoundException.withMessage("交付物不存在: " + deliverableId));
        if (!deliverable.getTaskId().equals(taskId)) {
            throw new IllegalArgumentException("交付物不属于该任务");
        }
        String storagePath = deliverable.getStoragePath();
        if (storagePath == null || storagePath.isBlank()) {
            throw ResourceNotFoundException.withMessage("交付物文件不存在: " + deliverableId);
        }
        Path filePath = resolveDocInsightPath(storagePath);
        if (!Files.exists(filePath)) {
            throw ResourceNotFoundException.withMessage("交付物文件不存在: " + deliverableId);
        }
        long fileSize;
        try {
            fileSize = Files.size(filePath);
        } catch (IOException e) {
            throw new IllegalStateException("无法读取文件大小", e);
        }
        String contentType = deliverable.getFileType() != null && !deliverable.getFileType().isBlank()
                ? deliverable.getFileType()
                : MediaType.APPLICATION_OCTET_STREAM_VALUE;
        return new DeliverableDownloadFile(
                deliverable.getName(),
                contentType,
                fileSize,
                new FileSystemResource(filePath)
        );
    }

    private Path resolveDocInsightPath(String fileUrl) {
        if (fileUrl.startsWith("http://") || fileUrl.startsWith("https://")) {
            throw new IllegalArgumentException("外部 URL 交付物暂不支持直接下载");
        }
        if (!fileUrl.startsWith("doc-insight://")) {
            throw new IllegalArgumentException("无效的文件 URL 格式: " + fileUrl);
        }
        String relativePath = fileUrl.substring("doc-insight://".length());
        if (relativePath.isBlank() || relativePath.contains("..")) {
            throw new IllegalArgumentException("无效的文件路径");
        }
        Path uploadRoot = (docInsightUploadDir == null || docInsightUploadDir.isBlank())
                ? Path.of(System.getProperty("java.io.tmpdir"), "xiyu-doc-insight-uploads")
                : Path.of(docInsightUploadDir);
        Path targetPath = uploadRoot.resolve(relativePath).normalize();
        if (!targetPath.startsWith(uploadRoot.toAbsolutePath().normalize())) {
            throw new IllegalArgumentException("文件路径越界");
        }
        return targetPath;
    }

    /**
     * File info record for deliverable download response.
     */
    public record DeliverableDownloadFile(
            String fileName,
            String contentType,
            long contentLength,
            Resource resource
    ) {}

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
