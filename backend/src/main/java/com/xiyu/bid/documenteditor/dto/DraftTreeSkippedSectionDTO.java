package com.xiyu.bid.documenteditor.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 批量草稿树导入跳过结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DraftTreeSkippedSectionDTO {

    private Long sectionId;

    private String sectionKey;

    private String title;

    private Boolean locked;

    private String reason;
}
