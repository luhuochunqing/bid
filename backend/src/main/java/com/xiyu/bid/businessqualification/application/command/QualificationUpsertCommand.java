package com.xiyu.bid.businessqualification.application.command;

import com.xiyu.bid.businessqualification.domain.model.QualificationAttachment;
import com.xiyu.bid.businessqualification.domain.valueobject.QualificationCategory;
import com.xiyu.bid.businessqualification.domain.valueobject.QualificationSubjectType;
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

    String agencyContact;
    String certScope;
    /** CO-530: 证书审核提醒，从 VARCHAR(200) 文本改为 DATE 日期选择 */
    LocalDate certReviewNote;
    /** CO-530: 审核日志附件 URL（非必填） */
    String auditLogFileUrl;
    String holderName;
    String retireReason;
    LocalDate issueDate;
    LocalDate expiryDate;
    Boolean reminderEnabled;
    Integer reminderDays;
    String fileUrl;
    Boolean retired;
    List<QualificationAttachment> attachments;
    /**
     * CO-368: 显式标记 fileUrl 是否被调用方设置。
     * 区分 "未传 fileUrl" (null) 与 "显式清空 fileUrl" (true + fileUrl=null)。
     * true 时按 command.getFileUrl() 写入（包括 null=清空）；null/false 时保留 existing.fileUrl()。
     */
    Boolean fileUrlExplicitlySet;
}
