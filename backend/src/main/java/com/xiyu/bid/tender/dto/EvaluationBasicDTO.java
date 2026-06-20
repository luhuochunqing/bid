package com.xiyu.bid.tender.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 评估表基础信息段 DTO（V130 三段式，V1026 字段重构）。
 *
 * <p>承载 9 个基础评估字段，与 TenderEvaluationBasic 实体对应。
 *
 * <p>CO-262: 新增 {@code projectPlanGapFiles} 字段，承载 CRM 商机关联回填的
 * GAP 附件引用（外部 URL），由 SubmissionService 在保存评估表时原子性持久化
 * 到 project_documents 表（linkedEntityType=EVALUATION_GAP）。
 */
public record EvaluationBasicDTO(

    /** 计划入围供应商数量。 */
    Integer plannedShortlistedCount,

    /** 电商MRO+办公流水金额（万）。 */
    BigDecimal mroOfficeFlowAmount,

    /** 招标文件不利项。 */
    String unfavorableItems,

    /** 风险预判。 */
    String riskAssessment,

    /** 项目经理综合评估是否有兜底方案。 */
    String contingencyPlan,

    /** 项目经理是否了解评标全流程。 */
    String processKnowledge,

    /** 需要的支持及其他关键信息备注。 */
    String supportNotes,

    /** 项目计划 GAP。 */
    String projectPlanGap,

    /** 客户营收（万）。 */
    BigDecimal customerRevenue,

    /** 项目计划 GAP 附件引用列表（CO-262 新增，可为 null 或空列表）。 */
    List<GapFileRef> projectPlanGapFiles
) {
    /**
     * 兼容旧构造器：未传 projectPlanGapFiles 时默认为 null。
     * <p>CO-262 P1-3 语义：null 表示"保留已有附件"（不清空、不新增）；
     * 空列表表示"明确清空"。调用方需根据场景选择合适的构造器。
     */
    public EvaluationBasicDTO(
            Integer plannedShortlistedCount,
            BigDecimal mroOfficeFlowAmount,
            String unfavorableItems,
            String riskAssessment,
            String contingencyPlan,
            String processKnowledge,
            String supportNotes,
            String projectPlanGap,
            BigDecimal customerRevenue) {
        this(plannedShortlistedCount, mroOfficeFlowAmount, unfavorableItems,
                riskAssessment, contingencyPlan, processKnowledge, supportNotes,
                projectPlanGap, customerRevenue, null);
    }

    /**
     * GAP 附件引用（外部 URL，不经过文件上传流程）。
     *
     * <p>{@code fileName} 仅用于展示，{@code fileUrl} 是 CRM 返回的外部下载地址。
     */
    public record GapFileRef(String fileName, String fileUrl) {}
}
