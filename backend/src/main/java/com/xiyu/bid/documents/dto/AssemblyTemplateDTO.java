package com.xiyu.bid.documents.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 文档组装模板数据传输对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssemblyTemplateDTO {

    private Long id;
    private String name;
    private String description;
    private String category;
    private String templateContent;
    private String variables;
    private Long createdBy;
    private LocalDateTime createdAt;
}
