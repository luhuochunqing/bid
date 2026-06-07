package com.xiyu.bid.documenteditor.dto;

import com.xiyu.bid.documenteditor.entity.SectionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 创建文档章节请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SectionCreateRequest {

    @NotNull(message = "Structure ID is required")
    private Long structureId;

    private Long parentId;

    @NotNull(message = "Section type is required")
    private SectionType sectionType;

    @NotBlank(message = "Title is required")
    private String title;

    private String content;

    private Integer orderIndex;

    private String metadata;
}
