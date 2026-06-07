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
     * 检查类型枚举
     */
    public enum CheckType {
        COMPLIANCE,           // 合规检查
        BID_DOCUMENT_QUALITY  // 标书文档质量核查
    }
}
