package com.xiyu.bid.documenteditor.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 批量草稿树导入请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DraftTreeUpsertRequest {

    private String structureName;

    @NotEmpty(message = "Draft sections are required")
    @Valid
    @Builder.Default
    private List<DraftTreeUpsertNodeRequest> sections = List.of();
}
