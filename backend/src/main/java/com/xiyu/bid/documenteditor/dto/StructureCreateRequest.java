package com.xiyu.bid.documenteditor.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 创建文档结构请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StructureCreateRequest {

    @NotNull(message = "Project ID is required")
    private Long projectId;

    @NotBlank(message = "Name is required")
    private String name;
}
