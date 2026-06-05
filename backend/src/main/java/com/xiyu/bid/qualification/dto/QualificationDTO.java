package com.xiyu.bid.qualification.dto;

import com.xiyu.bid.businessqualification.domain.valueobject.QualificationCategory;
import com.xiyu.bid.businessqualification.domain.valueobject.QualificationSubjectType;
import com.xiyu.bid.entity.Qualification;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 资质数据传输对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QualificationDTO {

    private Long id;
    private String name;
    private Qualification.Type type;
    private Qualification.Level level;
    private QualificationSubjectType subjectType;
    private String subjectName;
    private QualificationCategory category;
    private LocalDate issueDate;
    private LocalDate expiryDate;
    private String fileUrl;
    private String certificateNo;
    private String issuer;
    private String holder;
    private String holderName;
    private String remark;
    private Integer remainingDays;
    private String alertLevel;
    private String status;
    private Boolean borrowed;
    private String currentBorrower;
    private String currentBorrowDepartment;
    private String currentDepartment;
    private String currentBorrowPurpose;
    private String currentBorrowStatus;
    private String currentProjectId;
    private String borrowPurpose;
    private LocalDate currentExpectedReturnDate;
    private LocalDate expectedReturnDate;
    private Boolean reminderEnabled;
    private Integer reminderDays;
    private List<QualificationAttachmentDTO> attachments;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
