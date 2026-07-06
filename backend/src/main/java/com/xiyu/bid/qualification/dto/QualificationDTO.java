package com.xiyu.bid.qualification.dto;

import com.xiyu.bid.businessqualification.domain.valueobject.QualificationCategory;
import com.xiyu.bid.businessqualification.domain.valueobject.QualificationSubjectType;
import com.xiyu.bid.entity.Qualification;
import jakarta.validation.constraints.Size;
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
    private String level;
    private QualificationSubjectType subjectType;
    private String subjectName;
    private QualificationCategory category;
    private LocalDate issueDate;
    private LocalDate expiryDate;
    private String fileUrl;
    /**
     * CO-368: 调用方显式声明 fileUrl 已被设置（包括显式 null=清空）。
     * 缺省/null 表示未传 fileUrl 字段，后端保留 existing.fileUrl()。
     */
    private Boolean fileUrlExplicitlySet;
    private String certificateNo;
    private String issuer;
    private String agency;

    @Size(max = 200, message = "代理机构联系人超过200字符")
    private String agencyContact;

    private String certScope;
    private String certReviewNote;
    private String holder;
    private String holderName;
    private String remark;
    private Integer remainingDays;
    private String alertLevel;
    private String status;
    private String retireReason;
    private LocalDate currentExpectedReturnDate;
    private Boolean reminderEnabled;
    private Integer reminderDays;
    private List<QualificationAttachmentDTO> attachments;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
