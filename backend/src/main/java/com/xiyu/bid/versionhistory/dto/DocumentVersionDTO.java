// Input: DocumentVersion实体
// Output: 文档版本数据传输对象
// Pos: DTO/数据传输对象层
// 用于API响应的文档版本数据

package com.xiyu.bid.versionhistory.dto;

import com.xiyu.bid.versionhistory.entity.DocumentVersion;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 文档版本DTO
 * 用于返回文档版本信息给客户端
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentVersionDTO {

    private Long id;
    private Long projectId;
    private String documentId;
    private Integer versionNumber;
    private String content;
    private String filePath;
    private String changeSummary;
    private Long createdBy;
    private LocalDateTime createdAt;
    private Boolean isCurrent;

    /**
     * 从实体转换为DTO
     */
    public static DocumentVersionDTO fromEntity(DocumentVersion entity) {
        if (entity == null) {
            return null;
        }
        return DocumentVersionDTO.builder()
                .id(entity.getId())
                .projectId(entity.getProjectId())
                .documentId(entity.getDocumentId())
                .versionNumber(entity.getVersionNumber())
                .content(entity.getContent())
                .filePath(entity.getFilePath())
                .changeSummary(entity.getChangeSummary())
                .createdBy(entity.getCreatedBy())
                .createdAt(entity.getCreatedAt())
                .isCurrent(entity.getIsCurrent())
                .build();
    }

    /**
     * 将DTO转换为实体
     */
    public DocumentVersion toEntity() {
        return DocumentVersion.builder()
                .id(this.id)
                .projectId(this.projectId)
                .documentId(this.documentId)
                .versionNumber(this.versionNumber)
                .content(this.content)
                .filePath(this.filePath)
                .changeSummary(this.changeSummary)
                .createdBy(this.createdBy)
                .createdAt(this.createdAt)
                .isCurrent(this.isCurrent)
                .build();
    }
}
