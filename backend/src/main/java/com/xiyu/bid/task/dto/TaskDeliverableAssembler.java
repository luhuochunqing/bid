package com.xiyu.bid.task.dto;

import com.xiyu.bid.task.entity.TaskDeliverable;


/**
 * Pure assembler for TaskDeliverable DTO <-> Entity conversion.
 * No state, no dependencies, no side effects.
 */
public final class TaskDeliverableAssembler {

    /** Date-time formatter for display strings. */

    private TaskDeliverableAssembler() {
    }

    /**
     * Convert entity to DTO.
     *
     * @param entity the deliverable entity
     * @return the DTO representation
     */
    public static TaskDeliverableDTO toDTO(final TaskDeliverable entity) {
        return TaskDeliverableDTO.builder()
                .id(entity.getId())
                .taskId(entity.getTaskId())
                .name(entity.getName())
                .deliverableType(entity.getDeliverableType().name())
                .size(entity.getSize())
                .fileType(entity.getFileType())
                .url(buildUrl(entity))
                .version(entity.getVersion())
                .uploaderId(entity.getUploaderId())
                .uploaderName(entity.getUploaderName())
                .createdAt(entity.getCreatedAt())
                .build();
    }

    /**
     * Convert create request to entity.
     *
     * @param request      the creation request
     * @param taskId       owning task id
     * @param nextVersion  version number for this deliverable
     * @param uploaderId   uploader user id
     * @param uploaderName uploader display name
     * @return the persisted entity
     */
    public static TaskDeliverable toEntity(
            final TaskDeliverableCreateRequest request,
            final Long taskId,
            final int nextVersion,
            final Long uploaderId,
            final String uploaderName) {

        TaskDeliverable.DeliverableType type =
                parseType(request.getDeliverableType());
        return TaskDeliverable.builder()
                .taskId(taskId)
                .name(request.getName())
                .deliverableType(type)
                .size(request.getSize())
                .fileType(request.getFileType())
                .storagePath(request.getUrl())
                .version(nextVersion)
                .uploaderId(uploaderId)
                .uploaderName(uploaderName)
                .build();
    }

    private static String buildUrl(final TaskDeliverable entity) {
        if (entity.getStoragePath() != null
                && !entity.getStoragePath().isBlank()) {
            return entity.getStoragePath();
        }
        return null;
    }

    private static TaskDeliverable.DeliverableType parseType(
            final String value) {
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
}
