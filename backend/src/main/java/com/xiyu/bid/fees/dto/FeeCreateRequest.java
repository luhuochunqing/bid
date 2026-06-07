package com.xiyu.bid.fees.dto;

import com.xiyu.bid.fees.entity.Fee;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 创建费用请求DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeeCreateRequest {

    @NotNull(message = "Project ID is required")
    private Long projectId;

    @NotNull(message = "Fee type is required")
    private Fee.FeeType feeType;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
    @Digits(integer = 15, fraction = 2, message = "Amount must have valid format")
    private BigDecimal amount;

    @NotNull(message = "Fee date is required")
    private LocalDateTime feeDate;

    @Size(max = 1000, message = "Remarks must not exceed 1000 characters")
    private String remarks;
}
