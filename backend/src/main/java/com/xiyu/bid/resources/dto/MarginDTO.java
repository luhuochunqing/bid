package com.xiyu.bid.resources.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Margin ledger data transfer object. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarginDTO {
    /** Fee record ID. */
    private Long feeId;
    /** Project ID. */
    private Long projectId;
    /** Project name. */
    private String projectName;
    /** Owner unit. */
    private String ownerUnit;
    /** Project leader name. */
    private String projectLeaderName;
    /** Bidding leader name. */
    private String biddingLeaderName;
    /** Deposit amount. */
    private BigDecimal depositAmount;
    /** Payment date. */
    private LocalDateTime paymentDate;
    /** Payment method. */
    private String depositPaymentMethod;
    /** Payee name. */
    private String payeeName;
    /** Payee account. */
    private String payeeAccount;
    /** Expected return date. */
    private LocalDateTime expectedReturnDate;
    /** Returned amount. */
    private BigDecimal returnedAmount;
    /** Service fee amount. */
    private BigDecimal serviceFeeAmount;
    /** Actual return date. */
    private LocalDateTime actualReturnDate;
    /** Status code. */
    private String status;
    /** Status label. */
    private String statusLabel;
}
