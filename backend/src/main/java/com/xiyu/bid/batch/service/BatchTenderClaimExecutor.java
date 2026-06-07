package com.xiyu.bid.batch.service;

import com.xiyu.bid.batch.dto.BatchOperationResponse;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.repository.TenderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
class BatchTenderClaimExecutor {

    private static final int MAX_BATCH_SIZE = 100;

    private final TenderRepository tenderRepository;
    private final BatchOperationLogService batchOperationLogService;

    BatchOperationResponse execute(List<Long> tenderIds, Long userId) {
        BatchValidationPolicy.validateBatchInput(tenderIds, "Tender IDs", MAX_BATCH_SIZE);
        BatchValidationPolicy.validateUserId(userId);

        BatchOperationResponse response = BatchOperationResponse.builder()
                .operationType("CLAIM")
                .operationTime(LocalDateTime.now())
                .build();
        response.setTotalCount(tenderIds.size());

        List<Tender> tendersToClaim = new ArrayList<>();
        for (Long tenderId : tenderIds) {
            try {
                var tenderOpt = tenderRepository.findById(tenderId);
                if (tenderOpt.isEmpty()) {
                    response.addError(tenderId, "Tender not found with ID: " + tenderId, "NOT_FOUND");
                    continue;
                }
                Tender tender = tenderOpt.get();
                if (tender.getStatus() == Tender.Status.TRACKING && !isOwnedBy(tender, userId)) {
                    response.addError(tenderId, "Tender already being tracked by another user", "ALREADY_TRACKING");
                    continue;
                }
                tender.setStatus(Tender.Status.TRACKING);
                tendersToClaim.add(tender);
                response.addSuccess(tenderId);
            } catch (RuntimeException exception) {
                response.addError(tenderId, "Failed to claim tender: " + exception.getMessage(), "CLAIM_ERROR");
            }
        }

        if (!tendersToClaim.isEmpty()) {
            tenderRepository.saveAll(tendersToClaim);
        }
        response.setSuccess(response.getFailureCount() == 0);
        batchOperationLogService.record(response, "TENDER", "CLAIM", userId);
        return response;
    }

    private boolean isOwnedBy(Tender tender, Long userId) {
        return true;
    }
}
