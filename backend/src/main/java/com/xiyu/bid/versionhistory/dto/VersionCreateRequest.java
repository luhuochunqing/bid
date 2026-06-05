// Input: 版本创建请求
// Output: 版本创建请求DTO
// Pos: DTO/数据传输对象层
// 用于接收创建新版本的请求

package com.xiyu.bid.versionhistory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 版本创建请求DTO
 * 用于接收创建新文档版本的请求参数
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VersionCreateRequest {

    @NotNull(message = "Project ID is required")
    private Long projectId;

    private String documentId;

    @NotBlank(message = "Content cannot be empty")
    private String content;

    private String filePath;

    private String changeSummary;

    @NotNull(message = "Created by is required")
    private Long createdBy;
}
