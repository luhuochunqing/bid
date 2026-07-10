package com.xiyu.bid.batch.service;

import com.xiyu.bid.batch.core.BatchValidationPolicy;
import com.xiyu.bid.batch.core.TenderStatusTransitionPolicy;
import com.xiyu.bid.batch.dto.BatchOperationResponse;
import com.xiyu.bid.batch.dto.BatchTenderStatusUpdateRequest;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.webhook.domain.TenderStatusChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
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
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public BatchOperationResponse batchUpdateStatus(BatchTenderStatusUpdateRequest request, User currentUser) {
        BatchValidationPolicy.requireNonNull(request, "Batch tender status request cannot be null");
        BatchValidationPolicy.validateBatchInput(request.getTenderIds(), "Tender IDs");

        Tender.Status targetStatus = Tender.Status.valueOf(request.getStatus().trim().toUpperCase());
        BatchOperationResponse response = BatchOperationResponse.builder()
                .operationType("TENDER_STATUS_UPDATE")
                .operationTime(LocalDateTime.now())
                .build();
        response.setTotalCount(request.getTenderIds().size());

        List<Tender> changedTenders = new ArrayList<>();
        for (Long tenderId : request.getTenderIds()) {
            tenderRepository.findById(tenderId).ifPresentOrElse(
                    tender -> collectStatusUpdate(tender, targetStatus, changedTenders, response, currentUser),
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
            BatchOperationResponse response,
            User currentUser
    ) {
        try {
            projectAccessGuard.requireTender(tender.getId());
            TenderStatusTransitionPolicy.assertTransition(tender.getStatus(), targetStatus);
            if (tender.getStatus() != targetStatus) {
                Tender.Status previousStatus = tender.getStatus();
                tender.setStatus(targetStatus);
                Long operatorId = currentUser == null ? null : currentUser.getId();
                String operatorName = currentUser == null ? null : currentUser.getFullName();
                eventPublisher.publishEvent(TenderStatusChangedEvent.of(
                        tender.getId(), tender.getExternalId(),
                        previousStatus, targetStatus, tender.getTitle(),
                        null, operatorId, operatorName, null, null));
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
