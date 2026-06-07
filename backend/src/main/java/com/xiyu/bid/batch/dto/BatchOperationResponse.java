package com.xiyu.bid.batch.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchOperationResponse {
    private Boolean success;
    @Builder.Default private Integer successCount = 0;
    @Builder.Default private Integer failureCount = 0;
    @Builder.Default private Integer totalCount = 0;
    @Builder.Default private List<Long> successIds = new ArrayList<>();
    @Builder.Default private List<BatchOperationError> errors = new ArrayList<>();
    private LocalDateTime operationTime;
    private String operationType;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BatchOperationError {
        private Long itemId;
        private String errorMessage;
        private String errorCode;
    }

    public void addSuccess(Long id) {
        this.successIds.add(id);
        this.successCount++;
    }

    public void addError(Long id, String errorMessage, String errorCode) {
        this.errors.add(BatchOperationError.builder()
                .itemId(id)
                .errorMessage(errorMessage)
                .errorCode(errorCode)
                .build());
        this.failureCount++;
    }

    public void setTotalCount(int count) {
        this.totalCount = count;
    }

    public boolean isAllSuccess() {
        return this.successCount.equals(this.totalCount) && this.totalCount > 0;
    }

    public double getFailureRate() {
        if (totalCount == 0) return 0.0;
        return (double) failureCount / totalCount * 100;
    }
}
