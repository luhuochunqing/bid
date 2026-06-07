package com.xiyu.bid.documenteditor.dto;

import com.xiyu.bid.documenteditor.entity.SectionType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 批量草稿树节点导入请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DraftTreeUpsertNodeRequest {

    private String sectionKey;

    @NotBlank(message = "Title is required")
    private String title;

    private SectionType sectionType;

    private String content;

    private String runId;

    private List<String> sourceReferences;

    private BigDecimal confidence;

    private Boolean manual;

    @Valid
    @Builder.Default
    private List<DraftTreeUpsertNodeRequest> children = List.of();
}
