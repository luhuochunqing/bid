package com.xiyu.bid.tender.dto;

import java.math.BigDecimal;

/**
 * 评估表基础信息段 DTO（V130 三段式，V1026 字段重构）。
 *
 * <p>承载 9 个基础评估字段，与 TenderEvaluationBasic 实体对应。
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
    BigDecimal customerRevenue
) {}
