// Input: tender repository, linked project repository, data access guard, batch validation, and log service
// Output: tender batch command orchestration
// Pos: Service/业务层
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.batch.service;

import com.xiyu.bid.batch.core.BatchValidationPolicy;
import com.xiyu.bid.batch.dto.BatchOperationResponse;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.service.ProjectAccessScopeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 标书批处理命令
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BatchTenderCommandService {

    private final TenderRepository tenderRepository;
    private final ProjectRepository projectRepository;
    private final BatchValidationPolicy validationPolicy;
    private final BatchOperationLogService logService;
    private final ProjectAccessScopeService projectAccessScopeService;

    public BatchOperationResponse batchClaimTenders(List<Long> tenderIds, Long userId) {
        validationPolicy.validateBatchInput(tenderIds, "Tender IDs");
        validationPolicy.validateUserId(userId);
        log.info("Batch claiming tenders: count={}, userId={}", tenderIds.size(), userId);

        BatchOperationResponse response = newResponse("CLAIM", tenderIds.size());
        List<Tender> tendersToClaim = new ArrayList<>();
        for (Long tenderId : tenderIds) {
            try {
                var tenderOpt = tenderRepository.findById(tenderId);
                if (tenderOpt.isEmpty()) {
                    response.addError(tenderId, "Tender not found with ID: " + tenderId, "NOT_FOUND");
                    continue;
                }
                Tender tender = tenderOpt.get();
                requireLinkedProjectAccess(tender);
                if (tender.getStatus() == Tender.Status.TRACKING && !isOwnedBy(tender, userId)) {
                    response.addError(tenderId, "Tender already being tracked by another user", "ALREADY_TRACKING");
                    continue;
                }
                tender.setStatus(Tender.Status.TRACKING);
                tendersToClaim.add(tender);
                response.addSuccess(tenderId);
            } catch (RuntimeException exception) {
                addRuntimeError(response, tenderId, exception, "CLAIM_ERROR");
            }
        }
        if (!tendersToClaim.isEmpty()) {
            tenderRepository.saveAll(tendersToClaim);
        }
        complete(response, "TENDER", "CLAIM", userId);
        return response;
    }

    public BatchOperationResponse batchDeleteTenders(List<Long> tenderIds, Long userId) {
        BatchOperationResponse response = newResponse("DELETE", tenderIds.size());
        List<Tender> toDelete = new ArrayList<>();
        for (Long tenderId : tenderIds) {
            try {
                tenderRepository.findById(tenderId).ifPresent(tender -> {
                    requireLinkedProjectAccess(tender);
                    toDelete.add(tender);
                    response.addSuccess(tenderId);
                });
            } catch (RuntimeException exception) {
                addRuntimeError(response, tenderId, exception, "DELETE_ERROR");
            }
        }
        if (!toDelete.isEmpty()) {
            tenderRepository.deleteAll(toDelete);
        }
        complete(response, "TENDER", "DELETE", userId);
        return response;
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

    private boolean isOwnedBy(Tender tender, Long userId) {
        return true;
    }

    private void requireLinkedProjectAccess(Tender tender) {
        List<Project> linkedProjects = projectRepository.findByTenderId(tender.getId());
        for (Project project : linkedProjects) {
            projectAccessScopeService.assertCurrentUserCanAccessProject(project.getId());
        }
    }

    private void addRuntimeError(
            BatchOperationResponse response,
            Long itemId,
            RuntimeException exception,
            String fallbackCode
    ) {
        String code = BatchProjectAccessGuard.isAccessDenied(exception) ? "PERMISSION_DENIED" : fallbackCode;
        response.addError(itemId, exception.getMessage(), code);
    }
}
