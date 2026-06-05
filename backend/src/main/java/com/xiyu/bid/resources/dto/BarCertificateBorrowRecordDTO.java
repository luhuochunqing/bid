package com.xiyu.bid.resources.dto;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Value
@Builder
public class BarCertificateBorrowRecordDTO {
    Long id;
    Long certificateId;
    String borrower;
    Long projectId;
    String purpose;
    String remark;
    LocalDateTime borrowedAt;
    LocalDate expectedReturnDate;
    LocalDateTime returnedAt;
    BorrowStatus status;

    public enum BorrowStatus {
        BORROWED,
        RETURNED
    }
}
