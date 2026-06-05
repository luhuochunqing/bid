package com.xiyu.bid.qualification.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QualificationBorrowRecordDTO {
    private Long id;
    private Long qualificationId;
    private String qualificationName;
    private String borrower;
    private String department;
    private String projectId;
    private String purpose;
    private String remark;
    private String borrowedAt;
    private String expectedReturnDate;
    private String returnedAt;
    private String returnRemark;
    private String status;
}
