package com.xiyu.bid.qualification.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QualificationBorrowRequest {
    private String borrower;
    private String department;
    private String projectId;
    private String purpose;
    private LocalDate expectedReturnDate;
    private String remark;
}
