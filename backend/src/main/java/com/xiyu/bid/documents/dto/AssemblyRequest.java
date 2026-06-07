package com.xiyu.bid.documents.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文档组装请求DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssemblyRequest {

    @NotNull(message = "Template ID is required")
    private Long templateId;

    private String variables; // JSON string containing variable values

    private Long assembledBy;
}
