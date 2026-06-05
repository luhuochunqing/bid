package com.xiyu.bid.documenteditor.dto;

import com.xiyu.bid.documenteditor.entity.SectionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;

/**
 * 文档章节数据传输对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentSectionDTO {

    private Long id;
    private Long structureId;
    private Long parentId;
    private SectionType sectionType;
    private String title;
    private String content;
    private Integer orderIndex;
    private String metadata;
    private String owner;
    private LocalDate dueDate;
    private Boolean locked;
    private Long assignedBy;
    private Long lockedBy;
    private LocalDateTime lockedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * 子章节列表（用于构建树形结构）
     */
    @Builder.Default
    private List<DocumentSectionDTO> children = List.of();
}
