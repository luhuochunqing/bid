package com.xiyu.bid.batch.service;

import com.xiyu.bid.batch.dto.BatchApproveFeesRequest;
import com.xiyu.bid.batch.dto.BatchOperationResponse;
import com.xiyu.bid.fees.entity.Fee;
import com.xiyu.bid.util.InputSanitizer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
class BatchFeeApproveExecutor {

    private static final int MAX_BATCH_SIZE = 100;

    private final com.xiyu.bid.fees.repository.FeeRepository feeRepository;
    private final BatchOperationLogService batchOperationLogService;

    BatchOperationResponse execute(BatchApproveFeesRequest request, Long userId) {
        if (request == null) {
            throw new IllegalArgumentException("Batch approve fees request cannot be null");
        }
        BatchValidationPolicy.validateBatchInput(request.getFeeIds(), "Fee IDs", MAX_BATCH_SIZE);
        BatchValidationPolicy.validateUserId(userId);

        BatchOperationResponse response = BatchOperationResponse.builder()
                .operationType("PAY")
                .operationTime(LocalDateTime.now())
                .build();
        response.setTotalCount(request.getFeeIds().size());

        String paidBy = request.getPaidBy() != null ? request.getPaidBy() : "System (Batch " + userId + ")";
        List<Fee> feesToUpdate = new ArrayList<>();
        for (Long feeId : request.getFeeIds()) {
            try {
                var feeOpt = feeRepository.findById(feeId);
                if (feeOpt.isEmpty()) {
                    response.addError(feeId, "Fee not found with ID: " + feeId, "NOT_FOUND");
                    continue;
                }
                Fee fee = feeOpt.get();
                if (fee.getStatus() != Fee.Status.PENDING) {
                    response.addError(feeId,
                            "Only pending fees can be marked as paid. Current status: " + fee.getStatus(),
                            "INVALID_STATUS");
                    continue;
                }
                fee.setStatus(Fee.Status.PAID);
                fee.setPaymentDate(LocalDateTime.now());
                fee.setPaidBy(InputSanitizer.stripHtml(InputSanitizer.sanitizeString(paidBy, 200)));
                feesToUpdate.add(fee);
                response.addSuccess(feeId);
            } catch (RuntimeException exception) {
                response.addError(feeId, "Failed to approve fee: " + exception.getMessage(), "PAY_ERROR");
            }
        }

        if (!feesToUpdate.isEmpty()) {
            feeRepository.saveAll(feesToUpdate);
        }
        response.setSuccess(response.getFailureCount() == 0);
        batchOperationLogService.record(response, "FEE", "PAY", userId);
        return response;
    }
}
