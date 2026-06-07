// Input: 版本实体
// Output: 文档版本数据模型
// Pos: Entity/实体层
// 存储文档版本信息，支持版本历史跟踪

package com.xiyu.bid.versionhistory.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 文档版本实体
 * 用于跟踪文档的历史版本，支持版本比较和回滚功能
 */
@Entity
@Table(name = "document_versions", indexes = {
        @Index(name = "idx_document_version_project_id", columnList = "project_id"),
        @Index(name = "idx_document_id", columnList = "document_id"),
        @Index(name = "idx_project_current", columnList = "project_id, is_current")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "document_id")
    private String documentId; // 外部文档引用

    @Column(name = "version_number", nullable = false)
    private Integer versionNumber;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(name = "file_path")
    private String filePath; // 大文件存储路径

    @Column(name = "change_summary")
    private String changeSummary;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "is_current")
    private Boolean isCurrent;
}
