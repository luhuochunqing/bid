package com.xiyu.bid.documenteditor.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 批量草稿树导入结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DraftTreeUpsertResultDTO {

    private Long projectId;

    private Long structureId;

    private Boolean structureCreated;

    private Integer totalSections;

    private Integer createdSections;

    private Integer updatedSections;

    private Integer skippedSectionsCount;

    @Builder.Default
    private List<DraftTreeSkippedSectionDTO> skippedSections = List.of();
}
