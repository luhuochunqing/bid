package com.xiyu.bid.businessqualification.application.command;

import com.xiyu.bid.businessqualification.domain.model.QualificationAttachment;
import com.xiyu.bid.businessqualification.domain.valueobject.QualificationCategory;
import com.xiyu.bid.businessqualification.domain.valueobject.QualificationSubjectType;
import jakarta.validation.constraints.Pattern;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDate;
import java.util.List;

@Value
@Builder(toBuilder = true)
public class QualificationUpsertCommand {
    String name;
    String level;
    QualificationSubjectType subjectType;
    String subjectName;
    QualificationCategory category;
    String certificateNo;
    String issuer;
    String agency;

    @Pattern(regexp = "^(1[3-9]\\d{9}|(0\\d{2,3})[-]?\\d{7,8}|[^\\s@]+@[^\\s@]+\\.[^\\s@]+)$",
             message = "请输入有效的手机号、固话或邮箱")
    String agencyContact;
    String certScope;
    String certReviewNote;
    String holderName;
    String retireReason;
    LocalDate issueDate;
    LocalDate expiryDate;
    Boolean reminderEnabled;
    Integer reminderDays;
    String fileUrl;
    Boolean retired;
    List<QualificationAttachment> attachments;
}
