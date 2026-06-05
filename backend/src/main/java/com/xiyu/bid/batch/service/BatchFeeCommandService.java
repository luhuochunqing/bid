// Input: fee repository, validation policy, and log service
// Output: fee batch command orchestration
// Pos: Service/业务层
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.batch.service;

import com.xiyu.bid.batch.core.BatchValidationPolicy;
import com.xiyu.bid.batch.dto.BatchApproveFeesRequest;
import com.xiyu.bid.batch.dto.BatchOperationResponse;
import com.xiyu.bid.fees.entity.Fee;
import com.xiyu.bid.service.ProjectAccessScopeService;
import com.xiyu.bid.util.InputSanitizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 费用批处理命令
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BatchFeeCommandService {

    private final com.xiyu.bid.fees.repository.FeeRepository feeRepository;
    private final BatchValidationPolicy validationPolicy;
    private final BatchOperationLogService logService;
    private final ProjectAccessScopeService projectAccessScopeService;

    public BatchOperationResponse batchApproveFees(BatchApproveFeesRequest request, Long userId) {
        validationPolicy.requireNonNull(request, "Batch approve fees request cannot be null");
        validationPolicy.validateBatchInput(request.getFeeIds(), "Fee IDs");
        validationPolicy.validateUserId(userId);
        log.info("Batch approving fees: count={}, userId={}, paidBy={}",
                request.getFeeIds().size(), userId, request.getPaidBy());

        BatchOperationResponse response = newResponse("PAY", request.getFeeIds().size());
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
                projectAccessScopeService.assertCurrentUserCanAccessProject(fee.getProjectId());
                if (fee.getStatus() != Fee.Status.PENDING) {
                    response.addError(
                            feeId,
                            "Only pending fees can be marked as paid. Current status: " + fee.getStatus(),
                            "INVALID_STATUS"
                    );
                    continue;
                }
                fee.setStatus(Fee.Status.PAID);
                fee.setPaymentDate(LocalDateTime.now());
                fee.setPaidBy(InputSanitizer.stripHtml(InputSanitizer.sanitizeString(paidBy, 200)));
                feesToUpdate.add(fee);
                response.addSuccess(feeId);
            } catch (RuntimeException exception) {
                addRuntimeError(response, feeId, exception);
            }
        }
        if (!feesToUpdate.isEmpty()) {
            feeRepository.saveAll(feesToUpdate);
        }
        complete(response, "FEE", "PAY", userId);
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

    private void addRuntimeError(BatchOperationResponse response, Long feeId, RuntimeException exception) {
        if (BatchProjectAccessGuard.isAccessDenied(exception)) {
            response.addError(feeId, "Permission denied: fee is outside current data scope", "PERMISSION_DENIED");
            return;
        }
        response.addError(feeId, "Failed to approve fee: " + exception.getMessage(), "PAY_ERROR");
    }
}
