// Input: project_initiation_details 表行
// Output: JPA 实体 - WS-A 立项
// Pos: project/entity/ - 持久化模型
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.project.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "project_initiation_details")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectInitiationDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false, unique = true)
    private Long projectId;

    /** 业主单位 */
    @Column(name = "owner_unit", length = 255)
    private String ownerUnit;

    /** 入围家数 */
    @Column(name = "expected_bidders")
    private Integer expectedBidders;

    /** 客户类型（GOVERNMENT/CENTRAL_SOE/LOCAL_SOE/PRIVATE/FOREIGN） */
    @Column(name = "customer_type", length = 64)
    private String customerType;

    /** 项目类型（OFFICE/COMPREHENSIVE/COLLECTIVE/INDUSTRIAL/OTHER） */
    @Column(name = "project_type", length = 64)
    private String projectType;

    /** 营业收入 */
    @Column(name = "annual_revenue", precision = 20, scale = 2)
    @Deprecated
    private BigDecimal annualRevenue;

    /** 年度电商采购额(万) */
    @Column(name = "annual_ecommerce_amount", precision = 20, scale = 2)
    private BigDecimal annualEcommerceAmount;

    /** 合同期限（月） */
    @Column(name = "contract_period_months")
    private Integer contractPeriodMonths;

    /** 开标时间 */
    @Column(name = "bid_open_time")
    private LocalDateTime bidOpenTime;

    /** 投标月份（自动派生） */
    @Column(name = "bid_month", length = 16)
    private String bidMonth;

    /** 业务负责人用户 id */
    @Column(name = "owner_user_id")
    private Long ownerUserId;

    /** 归属部门快照 */
    @Column(name = "department_snapshot", length = 255)
    private String departmentSnapshot;

    /** 保证金额 */
    @Column(name = "deposit_amount", precision = 20, scale = 2)
    private BigDecimal depositAmount;

    /** 缴纳方式 */
    @Column(name = "deposit_payment_method", length = 64)
    private String depositPaymentMethod;

    /** 是否需要保证金(YES/NO) */
    @Column(name = "need_deposit", length = 16)
    @Builder.Default
    private String needDeposit = "NO";

    /** 竞争对手（可选） */
    @Column(name = "competitors", length = 1024)
    private String competitors;

    /** 招标文件不利项 */
    @Column(name = "tender_adverse_items", columnDefinition = "TEXT")
    private String tenderAdverseItems;

    /** 风险预判（举例说明） */
    @Column(name = "risk_assessment", columnDefinition = "TEXT")
    private String riskAssessment;

    /** 针对风险的兜底方案 */
    @Column(name = "risk_mitigation_plan", columnDefinition = "TEXT")
    private String riskMitigationPlan;

    /** 项目经理是否了解评标全流程(YES/NO) */
    @Column(name = "pm_understands_process", length = 16)
    private String pmUnderstandsProcess;

    /** 需要的支持及其他关键信息备注 */
    @Column(name = "support_needed", columnDefinition = "TEXT")
    private String supportNeeded;

    /** 项目计划GAP说明 */
    @Column(name = "project_plan_gap", columnDefinition = "TEXT")
    private String projectPlanGap;

    /** 提交后锁定 bidOpenTime/ownerUnit。 */
    @Column(name = "locked", nullable = false)
    @Builder.Default
    private Boolean locked = Boolean.FALSE;

    /** 立项审核状态（DRAFT/PENDING_REVIEW/APPROVED/REJECTED）。V125 新增。 */
    @Column(name = "review_status", length = 32, nullable = false)
    @Builder.Default
    private String reviewStatus = "DRAFT";

    /** 驳回理由（REJECTED 时必填）。 */
    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    /** 审核人 user id。 */
    @Column(name = "reviewed_by")
    private Long reviewedBy;

    /** 审核时间。 */
    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    /** 客户信息扩展（JSON 快照，来源 CRM）。 */
    @Column(name = "customer_info_json", columnDefinition = "JSON")
    private String customerInfoJson;

    /** 招标文件附件 id。 */
    @Column(name = "tender_document_id")
    private Long tenderDocumentId;

    /** AI 风险评级（LOW/MEDIUM/HIGH）。 */
    @Column(name = "ai_risk_level", length = 16)
    private String aiRiskLevel;

    /** AI风险评估说明 */
    @Column(name = "ai_risk_assessment_notes", columnDefinition = "TEXT")
    private String aiRiskAssessmentNotes;

    /** 客户等级。V133 新增。 */
    @Column(name = "customer_grade", length = 32)
    private String customerGrade;

    /** 投标状态。V133 新增。 */
    @Column(name = "bid_status", length = 32)
    private String bidStatus;

    /** 投标负责人姓名。V133 新增。 */
    @Column(name = "bidding_leader_name", length = 100)
    private String biddingLeaderName;

    /** 投标平台。V133 新增。 */
    @Column(name = "bidding_platform", length = 255)
    private String biddingPlatform;

    /** 中标状态。V133 新增。 */
    @Column(name = "bid_result_status", length = 32)
    private String bidResultStatus;

    /** 项目负责人姓名。V133 新增。 */
    @Column(name = "project_leader_name", length = 100)
    private String projectLeaderName;

    /** 负责人部门。V133 新增。 */
    @Column(name = "leader_department", length = 255)
    private String leaderDepartment;

    /** 总部所在地 */
    @Column(name = "headquarters_location", length = 255)
    private String headquartersLocation;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
        if (locked == null) locked = Boolean.FALSE;
        if (reviewStatus == null) reviewStatus = "DRAFT";
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
