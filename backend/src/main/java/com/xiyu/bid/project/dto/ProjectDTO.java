package com.xiyu.bid.project.dto;

import com.xiyu.bid.entity.Project;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 项目数据传输对象。
 * 列表投影字段（V133 新增）：对应 PRD §4.3 项目列表 16 列。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectDTO {

    private Long id;
    private String name;
    private Long tenderId;
    private Project.Status status;
    private Long managerId;
    private List<Long> teamMembers;
    /** 主投标负责人用户 ID（来自 project_lead_assignment 表，详情接口 enrich 填充） */
    private Long primaryLeadUserId;
    /** 副投标负责人用户 ID（来自 project_lead_assignment 表，详情接口 enrich 填充） */
    private Long secondaryLeadUserId;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private String sourceModule;
    private String sourceCustomerId;
    private String sourceCustomer;
    private String sourceOpportunityId;
    private String sourceReasoningSummary;
    private String competitorAnalysisJson;
    private String tasksJson;
    private String aiAnalysisJson;
    private String customer;
    private BigDecimal budget;
    private String industry;
    private String customerType;
    private String region;
    private String platform;
    private LocalDate deadline;
    private String description;
    private String remark;
    private String tagsJson;
    private String customerManager;
    private String customerManagerId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /* ===== 列表投影字段（PRD §4.3 16 列）。V133 新增 ===== */
    /** 项目阶段：INITIATED/DRAFTING/EVALUATING/RESULT_PENDING/RETROSPECTIVE/CLOSED */
    private String stage;
    /** 优先级 S/A/B/C */
    private String priority;
    /** 业主单位 */
    private String ownerUnit;
    /** 入围家数 */
    private Integer shortlistedCount;
    /** 客户营收（万） */
    private BigDecimal revenue;
    /** 开标时间 */
    private LocalDateTime bidOpenTime;
    /** 投标月份（yyyy-MM） */
    private String bidMonth;
    /** 项目类型 */
    private String projectType;
    /** 客户等级（A/B/C） */
    private String customerGrade;
    /** 投标状态 */
    private String bidStatus;
    /** 项目负责人姓名 */
    private String projectLeaderName;
    /** 项目负责人用户 ID，用于列表精确筛选 */
    private Long projectLeaderId;
    /** 负责人部门 */
    private String leaderDepartment;
    /** 投标负责人姓名 */
    private String biddingLeaderName;
    /** 主投标负责人用户 ID，用于列表精确筛选 */
    private Long biddingLeaderId;
    /** 副投标负责人用户 ID，用于列表精确筛选 */
    private Long secondaryBiddingLeaderId;
    /** 中标状态 */
    private String bidResultStatus;
    /** 投标平台 */
    private String biddingPlatform;
    /** 评标子状态（仅当 stage=EVALUATING 时有值） */
    private String evaluationSubStage;
}
