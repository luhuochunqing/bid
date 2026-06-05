package com.xiyu.bid.batch.service;

import com.xiyu.bid.batch.dto.BatchOperationResponse;
import com.xiyu.bid.entity.Task;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.TaskRepository;
import com.xiyu.bid.repository.TenderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
class BatchItemDeleteExecutor {

    private static final int MAX_BATCH_SIZE = 100;

    private final TenderRepository tenderRepository;
    private final TaskRepository taskRepository;
    private final BatchProjectDeleteExecutor batchProjectDeleteExecutor;
    private final BatchOperationLogService batchOperationLogService;

    BatchOperationResponse deleteItems(String itemType, List<Long> ids, Long userId, User.Role userRole) {
        if (itemType == null || itemType.trim().isEmpty()) {
            throw new IllegalArgumentException("Item type cannot be null or empty");
        }
        BatchValidationPolicy.validateBatchInput(ids, "Item IDs", MAX_BATCH_SIZE);
        BatchValidationPolicy.validateUserId(userId);
        BatchValidationPolicy.validateUserRole(userRole);

        String normalizedType = itemType.trim().toUpperCase(Locale.ROOT);
        return switch (normalizedType) {
            case "TENDER" -> deleteTenders(ids, userId);
            case "TASK" -> deleteTasks(ids, userId);
            case "PROJECT" -> batchProjectDeleteExecutor.execute(ids, userId, userRole);
            default -> throw new IllegalArgumentException("Unsupported item type: " + itemType);
        };
    }

    private BatchOperationResponse deleteTenders(List<Long> tenderIds, Long userId) {
        BatchOperationResponse response = BatchOperationResponse.builder()
                .operationType("DELETE")
                .operationTime(LocalDateTime.now())
                .build();
        response.setTotalCount(tenderIds.size());

        List<Tender> toDelete = new ArrayList<>();
        for (Long id : tenderIds) {
            try {
                tenderRepository.findById(id).ifPresent(tender -> {
                    toDelete.add(tender);
                    response.addSuccess(id);
                });
            } catch (RuntimeException exception) {
                response.addError(id, exception.getMessage(), "DELETE_ERROR");
            }
        }
        if (!toDelete.isEmpty()) {
            tenderRepository.deleteAll(toDelete);
        }
        response.setSuccess(response.getFailureCount() == 0);
        batchOperationLogService.record(response, "TENDER", "DELETE", userId);
        return response;
    }

    private BatchOperationResponse deleteTasks(List<Long> taskIds, Long userId) {
        BatchOperationResponse response = BatchOperationResponse.builder()
                .operationType("DELETE")
                .operationTime(LocalDateTime.now())
                .build();
        response.setTotalCount(taskIds.size());

        List<Task> toDelete = new ArrayList<>();
        for (Long id : taskIds) {
            try {
                taskRepository.findById(id).ifPresent(task -> {
                    toDelete.add(task);
                    response.addSuccess(id);
                });
            } catch (RuntimeException exception) {
                response.addError(id, exception.getMessage(), "DELETE_ERROR");
            }
        }
        if (!toDelete.isEmpty()) {
            taskRepository.deleteAll(toDelete);
        }
        response.setSuccess(response.getFailureCount() == 0);
        batchOperationLogService.record(response, "TASK", "DELETE", userId);
        return response;
    }
}
