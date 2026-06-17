package com.xiyu.bid.integration.external;

import com.xiyu.bid.tender.dto.ContactDTO;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 外部系统推送标讯请求 DTO（接口规范 v2.0）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenderPushRequest {

    @NotBlank(message = "sourceSystem 不能为空")
    @Size(max = 50)
    private String sourceSystem;

    @NotBlank(message = "sourceId 不能为空")
    @Size(max = 100)
    private String sourceId;

    @NotBlank(message = "title 不能为空")
    @Size(max = 500)
    private String title;

    @Size(max = 500)
    private String customerName;

    private LocalDate publishDate;

    private String dueDate;

    @DecimalMin(value = "0", message = "预算金额不能为负数")
    @DecimalMax(value = "999999999999", message = "预算金额超出范围")
    private BigDecimal budgetAmount;

    // ── 基本信息字段 ───────────────────────────────────────────────

    @Size(max = 100)
    private String region;

    @Size(max = 100)
    private String industry;

    @Size(max = 255)
    private String tenderAgency;

    /** 开标时间（ISO datetime） */
    private String bidOpeningTime;

    /** 报名截止时间（ISO datetime） */
    private String registrationDeadline;

    @Size(max = 100)
    private String customerType;

    @Size(max = 10)
    private String priority;

    @Size(max = 20)
    private String projectType;

    @Size(max = 100)
    private String sourcePlatform;

    @Size(max = 200)
    private String source;

    private List<String> tags;

    // ── 联系人 ─────────────────────────────────────────────────────

    /** 联系人数组 */
    private List<ContactDTO> contactInfo;

    @Size(max = 5000)
    private String contentDesc;

    /** 标讯信息（最长 5000 字符） */
    @Size(max = 5000)
    private String tenderInfo;

    /** 项目负责人姓名 */
    @Size(max = 100)
    private String projectManagerName;

    /** 项目部门 */
    @Size(max = 100)
    private String department;

    /** 创建人姓名（覆盖系统默认值） */
    @Size(max = 100)
    private String creatorName;

    /** 创建时间（格式 yyyy-MM-dd HH:mm 或 yyyy-MM-ddTHH:mm） */
    private String createDate;

    private List<AttachmentRef> attachments;

    @Builder.Default
    private Boolean forceUpdate = false;

    /** CRM 商机 ID（传入后自动关联商机并分配项目负责人）。 */
    private String crmId;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AttachmentRef {
        @NotBlank
        @Size(max = 500)
        private String fileName;

        @NotBlank
        @Size(max = 2000)
        private String fileUrl;
    }
}
