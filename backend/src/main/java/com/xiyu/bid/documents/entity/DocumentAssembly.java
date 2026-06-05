package com.xiyu.bid.documents.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 文档组装记录实体
 * 记录使用模板组装文档的历史记录
 */
@Entity
@Table(name = "document_assemblies", indexes = {
    @Index(name = "idx_assembly_project", columnList = "project_id"),
    @Index(name = "idx_assembly_template", columnList = "template_id"),
    @Index(name = "idx_assembly_project_template", columnList = "project_id, template_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentAssembly {

    /**
     * 主键ID
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 关联的项目ID
     */
    @Column(name = "project_id", nullable = false)
    private Long projectId;

    /**
     * 使用的模板ID
     */
    @Column(name = "template_id")
    private Long templateId;

    /**
     * 组装后的文档内容
     */
    @Column(columnDefinition = "TEXT")
    private String assembledContent;

    /**
     * 实际填充的变量值，JSON格式
     */
    @Column(columnDefinition = "TEXT")
    private String variables;

    /**
     * 组装人ID
     */
    @Column(name = "assembled_by")
    private Long assembledBy;

    /**
     * 组装时间
     */
    @Column(name = "assembled_at", nullable = false, updatable = false)
    private LocalDateTime assembledAt;

    @PrePersist
    protected void onCreate() {
        assembledAt = LocalDateTime.now();
    }
}
