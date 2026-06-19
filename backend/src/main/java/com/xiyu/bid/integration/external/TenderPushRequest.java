package com.xiyu.bid.integration.external;

import com.xiyu.bid.tender.dto.ContactDTO;
import com.xiyu.bid.tender.dto.EvaluationBasicDTO;
import com.xiyu.bid.tender.dto.EvaluationRecommendationDTO;
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
import java.util.Map;

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

    // ── 项目评估（v3.1 新增）─────────────────────────────────────────

    /** 项目评估数据 */
    private EvaluationUpdate evaluation;

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

    /**
     * CRM 商机标识（对外公开字段，语义同 {@link #crmId}）。
     * <p>CO-276：CRM 通过 PUT/POST 推送时使用 crmOpportunityId 字段名，与代码内 crmId 不一致，
     * 导致 Jackson 反序列化时丢弃该字段。新增此公开别名字段兼容 CRM 文档字段名，
     * 业务侧用 {@code firstNonBlank(crmOpportunityId, crmId)} 合并取值，两者任一非空即可。
     * <p>⚠️ CO-277 实测纠正：CRM 实际推送的是商机<strong>主键 id</strong>（纯数字如 20916），
     * 而非商机编号 code（CC... 格式）。本字段值可能是 id 或 code，
     * 由 {@code CrmTenderLinkService.applyCrmLinkAndAssignment} 自动识别并按 id 反查 code 后落库。
     * 因此 {@code crmId}/{@code crmOpportunityId} 的语义是"CRM 推送的商机标识"，不保证是 code 格式。
     */
    private String crmOpportunityId;

    /** CRM 商机名称（对外公开字段，与 crmOpportunityId 配套，可选）。 */
    private String crmOpportunityName;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EvaluationUpdate {
        private EvaluationBasicDTO evaluationBasic;
        private List<Map<String, Object>> evaluationCustomerInfos;
        private EvaluationRecommendationDTO evaluationRecommendation;
    }

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
