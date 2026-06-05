package com.xiyu.bid.documents.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 创建模板请求DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TemplateCreateRequest {

    @NotBlank(message = "Template name is required")
    private String name;

    private String description;

    private String category;

    @NotBlank(message = "Template content is required")
    private String templateContent;

    private String variables;

    private Long createdBy;
}
