package com.xiyu.bid.tender.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 标讯评估表-基础信息段（V130 三段式重构新增）。
 *
 * <p>承载 8 个基础评估字段，与 TenderEvaluation 一对一关系。
 * 对应 PRD §4.2.5 评估表第一段「基础信息」。
 */
@Entity
@Table(name = "tender_evaluation_basics")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenderEvaluationBasic {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "evaluation_id", nullable = false, unique = true)
    private TenderEvaluation evaluation;

    /** 计划入围供应商数量。 */
    @Column(name = "planned_shortlisted_count")
    private Integer plannedShortlistedCount;

    /** 电商MRO+办公流水金额（万）。 */
    @Column(name = "mro_office_flow_amount", precision = 15, scale = 2)
    private BigDecimal mroOfficeFlowAmount;

    /** 招标文件不利项。 */
    @Column(name = "unfavorable_items", length = 5000)
    private String unfavorableItems;

    /** 风险预判。 */
    @Column(name = "risk_assessment", length = 5000)
    private String riskAssessment;

    /** 项目经理综合评估是否有兜底方案。 */
    @Column(name = "contingency_plan", length = 5000)
    private String contingencyPlan;

    /** 项目经理是否了解评标全流程。 */
    @Column(name = "process_knowledge", length = 5000)
    private String processKnowledge;

    /** 需要的支持及其他关键信息备注。 */
    @Column(name = "support_notes", length = 5000)
    private String supportNotes;

    /** 项目计划 GAP。 */
    @Column(name = "project_plan_gap", length = 5000)
    private String projectPlanGap;

    /** 客户营收（万）。 */
    @Column(name = "customer_revenue", precision = 15, scale = 2)
    private BigDecimal customerRevenue;
}
