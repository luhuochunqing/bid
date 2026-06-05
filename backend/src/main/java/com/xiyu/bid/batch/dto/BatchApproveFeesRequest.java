package com.xiyu.bid.batch.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request DTO for batch marking fees as paid.
 * Allows marking multiple fee records as paid at once.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchApproveFeesRequest {

    @NotEmpty(message = "Fee IDs list cannot be empty")
    private List<@NotNull(message = "Fee ID cannot be null") Long> feeIds;

    /**
     * Payer information for all fees (optional)
     */
    private String paidBy;

    /**
     * Optional payer ID for audit trail
     */
    private Long payerId;
}
