package com.xiyu.bid.compliance.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 合规检查结果实体
 * 记录项目或标书的合规检查结果
 */
@Entity
@Table(name = "compliance_check_results", indexes = {
    @Index(name = "idx_result_project", columnList = "project_id"),
    @Index(name = "idx_result_tender", columnList = "tender_id"),
    @Index(name = "idx_result_status", columnList = "overall_status"),
    @Index(name = "idx_result_checked_at", columnList = "checked_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComplianceCheckResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 关联的项目ID
     */
    @Column(name = "project_id")
    private Long projectId;

    /**
     * 关联的标书ID
     */
    @Column(name = "tender_id")
    private Long tenderId;

    /**
     * 整体合规状态
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private Status overallStatus;

    /**
     * 检查详情（JSON格式）
     * 存储具体的检查结果和问题列表
     */
    @Column(columnDefinition = "TEXT")
    private String checkDetails;

    /**
     * 风险分数（0-100）
     * 分数越高，风险越大
     */
    @Column(nullable = false)
    private Integer riskScore;

    /**
     * 检查时间
     */
    @Column(name = "checked_at", nullable = false)
    private LocalDateTime checkedAt;

    /**
     * 检查人
     */
    @Column(name = "checked_by", length = 100)
    private String checkedBy;

    /**
     * 检查类型
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "check_type", nullable = false, length = 50)
    @Builder.Default
    private CheckType checkType = CheckType.COMPLIANCE;

    /**
     * 异步任务UUID
     */
    @Column(name = "task_id", length = 64)
    private String taskId;

    /**
     * 异步任务状态
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 32)
    @Builder.Default
    private TaskStatus status = TaskStatus.COMPLETED;

    /**
     * 任务进度百分比 (0-100)
     */
    @Column(name = "progress")
    @Builder.Default
    private Integer progress = 100;

    /**
     * 当前步骤说明
     */
    @Column(name = "current_step", length = 128)
    private String currentStep;

    /**
     * 失败错误信息
     */
    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    /**
     * 投标文件名
     */
    @Column(name = "bid_file_name")
    private String bidFileName;

    /**
     * 投标文件SHA-256
     */
    @Column(name = "bid_file_hash", length = 64)
    private String bidFileHash;

    /**
     * 招标文件SHA-256
     */
    @Column(name = "tender_file_hash", length = 64)
    private String tenderFileHash;

    /**
     * 通过项数
     */
    @Column(name = "passed_count")
    @Builder.Default
    private Integer passedCount = 0;

    /**
     * 不通过项数
     */
    @Column(name = "failed_count")
    @Builder.Default
    private Integer failedCount = 0;

    /**
     * 待确认项数
     */
    @Column(name = "manual_review_count")
    @Builder.Default
    private Integer manualReviewCount = 0;

    /**
     * 总检查项数
     */
    @Column(name = "total_count")
    @Builder.Default
    private Integer totalCount = 25;

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
        if (checkedAt == null) {
            checkedAt = LocalDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * 合规状态枚举
     */
    public enum Status {
        COMPLIANT,         // 合规
        NON_COMPLIANT,     // 不合规
        PARTIAL_COMPLIANT, // 部分合规
        WARNING            // 警告
    }

    /**
     * 任务流转状态枚举
     */
    public enum TaskStatus {
        INIT,       // 初始化
        PARSING,    // 文件解析中
        CHECKING,   // 质量核查中
        COMPLETED,  // 已完成
        FAILED      // 失败
    }

    /**
     * 检查类型枚举
     */
    public enum CheckType {
        COMPLIANCE,           // 合规检查
        BID_DOCUMENT_QUALITY  // 标书文档质量核查
    }
}
