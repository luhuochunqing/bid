package com.xiyu.bid.fees.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 更新费用请求DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeeUpdateRequest {

    @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
    @Digits(integer = 15, fraction = 2, message = "Amount must have valid format")
    private BigDecimal amount;

    private LocalDateTime feeDate;

    @Size(max = 1000, message = "Remarks must not exceed 1000 characters")
    private String remarks;
}
