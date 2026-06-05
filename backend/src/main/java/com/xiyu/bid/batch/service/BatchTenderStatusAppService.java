package com.xiyu.bid.batch.service;

import com.xiyu.bid.batch.core.BatchValidationPolicy;
import com.xiyu.bid.batch.core.TenderStatusTransitionPolicy;
import com.xiyu.bid.batch.dto.BatchOperationResponse;
import com.xiyu.bid.batch.dto.BatchTenderStatusUpdateRequest;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.TenderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BatchTenderStatusAppService {

    private final TenderRepository tenderRepository;
    private final BatchProjectAccessGuard projectAccessGuard;
    private final BatchOperationLogService batchOperationLogService;
    private final BatchValidationPolicy validationPolicy;
    private final TenderStatusTransitionPolicy transitionPolicy;

    @Transactional
    public BatchOperationResponse batchUpdateStatus(BatchTenderStatusUpdateRequest request, User currentUser) {
        validationPolicy.requireNonNull(request, "Batch tender status request cannot be null");
        validationPolicy.validateBatchInput(request.getTenderIds(), "Tender IDs");

        Tender.Status targetStatus = Tender.Status.valueOf(request.getStatus().trim().toUpperCase());
        BatchOperationResponse response = BatchOperationResponse.builder()
                .operationType("TENDER_STATUS_UPDATE")
                .operationTime(LocalDateTime.now())
                .build();
        response.setTotalCount(request.getTenderIds().size());

        List<Tender> changedTenders = new ArrayList<>();
        for (Long tenderId : request.getTenderIds()) {
            tenderRepository.findById(tenderId).ifPresentOrElse(
                    tender -> collectStatusUpdate(tender, targetStatus, changedTenders, response),
                    () -> response.addError(tenderId, "Tender not found", "NOT_FOUND")
            );
        }

        if (!changedTenders.isEmpty()) {
            tenderRepository.saveAll(changedTenders);
        }
        response.setSuccess(response.getFailureCount() == 0);
        batchOperationLogService.record(response, "TENDER", "STATUS_UPDATE", currentUser == null ? null : currentUser.getId());
        return response;
    }

    private void collectStatusUpdate(
            Tender tender,
            Tender.Status targetStatus,
            List<Tender> changedTenders,
            BatchOperationResponse response
    ) {
        try {
            projectAccessGuard.requireTender(tender.getId());
            transitionPolicy.assertTransition(tender.getStatus(), targetStatus);
            if (tender.getStatus() != targetStatus) {
                tender.setStatus(targetStatus);
                changedTenders.add(tender);
            }
            response.addSuccess(tender.getId());
        } catch (IllegalArgumentException exception) {
            response.addError(tender.getId(), exception.getMessage(), "INVALID_STATUS_TRANSITION");
        } catch (RuntimeException exception) {
            addRuntimeError(response, tender.getId(), exception);
        }
    }

    private void addRuntimeError(BatchOperationResponse response, Long itemId, RuntimeException exception) {
        String code = BatchProjectAccessGuard.isAccessDenied(exception) ? "PERMISSION_DENIED" : "STATUS_UPDATE_ERROR";
        response.addError(itemId, exception.getMessage(), code);
    }
}
