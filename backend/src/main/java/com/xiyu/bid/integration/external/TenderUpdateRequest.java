package com.xiyu.bid.integration.external;

import com.xiyu.bid.tender.dto.ContactDTO;
import com.xiyu.bid.tender.dto.EvaluationBasicDTO;
import com.xiyu.bid.tender.dto.EvaluationRecommendationDTO;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
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
 * 外部系统更新标讯请求 DTO（接口规范 v2.0）。
 * 所有字段均可选，仅传入非空字段会被更新。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenderUpdateRequest {

    /** 标讯内部 ID（可选，传入后可与路径参数交叉校验） */
    private Long tenderId;

    @Size(max = 500)
    private String title;

    @Size(max = 500)
    private String customerName;

    private LocalDate publishDate;

    private String dueDate;

    @DecimalMin(value = "0", message = "预算金额不能为负数")
    @DecimalMax(value = "999999999999", message = "预算金额超出范围")
    private BigDecimal budgetAmount;

    // ── 基本信息字段（均可选）───────────────────────────────────────

    @Size(max = 100)
    private String region;

    @Size(max = 100)
    private String industry;

    @Size(max = 255)
    private String tenderAgency;

    private String bidOpeningTime;

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

    /** 项目负责人姓名（传入后同步反查本地用户 ID 落库） */
    @Size(max = 100)
    private String projectManagerName;

    private List<TenderPushRequest.AttachmentRef> attachments;

    // ── 项目评估（v3.1 新增）─────────────────────────────────────────

    /** 项目评估数据 */
    private EvaluationUpdate evaluation;

    /**
     * CRM 商机主键 id（纯数字，如 20916）。
     * <p>用于调用 CRM detail 接口查询项目负责人。不直接存入 crm_opportunity_id 列。
     */
    private String crmId;

    /**
     * CRM 商机编号 code（CC 前缀格式，如 CC2026070932）。
     * <p>非空时直接存入 tender.crm_opportunity_id，用于 webhook 回传 CRM。
     * 与 {@link #crmId} 是两个独立字段：crmId 是主键 id，crmOpportunityId 是编号 code。
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
}
