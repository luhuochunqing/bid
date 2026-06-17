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

    private List<TenderPushRequest.AttachmentRef> attachments;

    // ── 项目评估（v3.1 新增）─────────────────────────────────────────

    /** 项目评估数据 */
    private EvaluationUpdate evaluation;

    /** CRM 商机 ID（传入后自动关联商机并分配项目负责人）。 */
    private String crmId;

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
