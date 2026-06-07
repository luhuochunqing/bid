package com.xiyu.bid.documenteditor.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 更新文档章节请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SectionUpdateRequest {

    private String title;

    private String content;

    private String metadata;

    private Integer orderIndex;
}
