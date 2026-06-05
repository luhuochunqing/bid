package com.xiyu.bid.documents.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 文档组装模板实体
 * 用于存储可重用的文档模板，支持变量占位符
 */
@Entity
@Table(name = "assembly_templates", indexes = {
    @Index(name = "idx_template_category", columnList = "category"),
    @Index(name = "idx_template_created_by", columnList = "created_by")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssemblyTemplate {

    /**
     * 主键ID
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 模板名称
     */
    @Column(nullable = false, length = 200)
    private String name;

    /**
     * 模板描述
     */
    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * 模板分类（如：BIDDING_DOCUMENT、QUALIFICATION、CONTRACT等）
     */
    private String category;

    /**
     * 模板内容，支持 ${variableName} 占位符
     */
    @Column(columnDefinition = "TEXT", nullable = false)
    private String templateContent;

    /**
     * 变量定义，JSON格式描述所需的变量及其类型
     */
    @Column(columnDefinition = "TEXT")
    private String variables;

    /**
     * 创建人ID
     */
    @Column(name = "created_by")
    private Long createdBy;

    /**
     * 创建时间
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
