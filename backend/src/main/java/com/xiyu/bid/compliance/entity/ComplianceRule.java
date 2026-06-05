package com.xiyu.bid.compliance.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 合规规则实体
 * 定义各类合规检查规则
 */
@Entity
@Table(name = "compliance_rules", indexes = {
    @Index(name = "idx_rule_type", columnList = "rule_type"),
    @Index(name = "idx_rule_enabled", columnList = "enabled")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComplianceRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 规则名称
     */
    @Column(nullable = false, length = 200)
    private String name;

    /**
     * 规则类型
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private RuleType ruleType;

    /**
     * 规则定义（JSON格式）
     * 存储规则的具体参数和条件
     */
    @Column(columnDefinition = "TEXT")
    private String ruleDefinition;

    /**
     * 规则描述
     */
    @Column(length = 1000)
    private String description;

    /**
     * 是否启用
     */
    @Column(nullable = false)
    @Builder.Default
    private Boolean enabled = true;

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
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * 规则类型枚举
     */
    public enum RuleType {
        QUALIFICATION,  // 资质检查
        DOCUMENT,       // 文档检查
        FINANCIAL,      // 财务检查
        EXPERIENCE,     // 经验检查
        DEADLINE        // 期限检查
    }
}
