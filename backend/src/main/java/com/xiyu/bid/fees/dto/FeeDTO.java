package com.xiyu.bid.fees.dto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 费用数据传输对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeeDTO {

    private Long id;
    private Long projectId;
    private FeeType feeType;
    private BigDecimal amount;
    private LocalDateTime feeDate;
    private Status status;
    private LocalDateTime paymentDate;
    private LocalDateTime returnDate;
    private String paidBy;
    private String returnTo;
    private String remarks;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public enum FeeType {
        BID_BOND,
        SERVICE_FEE,
        DOCUMENT_FEE,
        TRAVEL_FEE,
        NOTARY_FEE,
        OTHER_FEE
    }

    public enum Status {
        PENDING,
        PAID,
        RETURNED,
        CANCELLED
    }
}
