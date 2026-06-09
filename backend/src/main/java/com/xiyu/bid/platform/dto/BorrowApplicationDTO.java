package com.xiyu.bid.platform.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Response DTO for a borrow application. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BorrowApplicationDTO {

    private Long id;
    private Long accountId;
    private Long applicantId;
    private Long custodianId;
    private String purpose;
    private String projectName;
    private LocalDateTime expectedReturnAt;
    private String status;
    private String rejectReason;
    private LocalDateTime approvedAt;
    private LocalDateTime returnedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
