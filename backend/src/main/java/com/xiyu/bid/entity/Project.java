package com.xiyu.bid.entity;

import jakarta.persistence.Column;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Entity;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 项目实体
 * 管理投标项目信息
 */
@Entity
@Table(name = "projects", indexes = {
    @Index(name = "idx_project_status", columnList = "status"),
    @Index(name = "idx_project_manager", columnList = "manager_id"),
    @Index(name = "idx_project_tender", columnList = "tender_id"),
    @Index(name = "idx_project_customer_type", columnList = "customer_type"),
    @Index(name = "idx_project_dates", columnList = "start_date, end_date")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 项目名称
     */
    @Column(nullable = false, length = 500)
    private String name;

    /**
     * 关联的标讯ID
     */
    @Column(name = "tender_id", nullable = false)
    private Long tenderId;

    /**
     * 项目状态
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Project.Status status = Project.Status.PENDING_INITIATION;

    /**
     * PRD §5.4 6 阶段 FSM stage（V99 schema 新增列；保存为 ProjectStage 名称字符串）。
     */
    @Column(name = "stage", nullable = false, length = 32)
    @Builder.Default
    private String stage = "INITIATED";

    /**
     * 项目经理ID
     */
    @Column(name = "manager_id", nullable = false)
    private Long managerId;

    /**
     * 团队成员ID列表（JSON格式存储）
     */
    @ElementCollection
    @CollectionTable(name = "project_team_members", joinColumns = @JoinColumn(name = "project_id"))
    @Column(name = "member_id")
    @Builder.Default
    private List<Long> teamMembers = new ArrayList<>();

    /**
     * 开始日期
     */
    @Column(name = "start_date")
    private LocalDateTime startDate;

    /**
     * 结束日期
     */
    @Column(name = "end_date")
    private LocalDateTime endDate;

    /**
     * 立项时间（首次进入 INITIATED 阶段，由 ProjectService.createProject 填充）
     */
    @Column(name = "initiated_at")
    private LocalDateTime initiatedAt;

    /**
     * 评标时间（首次进入 EVALUATING 阶段，由 ProjectStageService.requestTransition 填充）
     */
    @Column(name = "evaluating_at")
    private LocalDateTime evaluatingAt;

    /**
     * 结项时间（首次进入 CLOSED 阶段，由 ProjectStageService.requestTransition 填充）
     */
    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Column(name = "source_module", length = 100)
    private String sourceModule;

    @Column(name = "source_customer_id", length = 100)
    private String sourceCustomerId;

    @Column(name = "source_customer", length = 255)
    private String sourceCustomer;

    @Column(name = "source_opportunity_id", length = 100)
    private String sourceOpportunityId;

    @Column(name = "source_reasoning_summary", columnDefinition = "TEXT")
    private String sourceReasoningSummary;

    @Column(name = "competitor_analysis_json", columnDefinition = "TEXT")
    private String competitorAnalysisJson;

    @Column(name = "tasks_json", columnDefinition = "TEXT")
    private String tasksJson;

    @Column(name = "ai_analysis_json", columnDefinition = "TEXT")
    private String aiAnalysisJson;

    @Column(name = "customer", length = 255)
    private String customer;

    @Column(name = "budget", precision = 14, scale = 2)
    private BigDecimal budget;

    @Column(name = "industry", length = 50)
    private String industry;

    @Column(name = "customer_type", length = 100)
    private String customerType;

    @Column(name = "region", length = 100)
    private String region;

    @Column(name = "platform", length = 255)
    private String platform;

    @Column(name = "deadline")
    private LocalDate deadline;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "remark", columnDefinition = "TEXT")
    private String remark;

    @Column(name = "tags_json", length = 1000)
    private String tagsJson;

    @Column(name = "customer_manager", length = 100)
    private String customerManager;

    @Column(name = "customer_manager_id", length = 100)
    private String customerManagerId;

    /**
     * 创建时间
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        // 档案 4.1.1.1.1：项目创建时自动记录立项时间（首次写入原则）
        if (initiatedAt == null) {
            initiatedAt = LocalDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * 项目状态枚举（产品蓝图 §4.3 — 8 值）
     */
    public enum Status {
        PENDING_INITIATION,  // 待立项
        INITIATED,           // 已立项
        BIDDING,             // 投标中
        EVALUATING,          // 评标中
        WON,                 // 已中标
        LOST,                // 未中标
        FAILED,              // 已流标
        ABANDONED;           // 已放弃

        public String displayName() {
            return switch (this) {
                case PENDING_INITIATION -> "待立项";
                case INITIATED -> "已立项";
                case BIDDING -> "投标中";
                case EVALUATING -> "评标中";
                case WON -> "已中标";
                case LOST -> "未中标";
                case FAILED -> "已流标";
                case ABANDONED -> "已放弃";
            };
        }

        public boolean isTerminal() {
            return this == WON || this == LOST || this == FAILED || this == ABANDONED;
        }
    }
}
