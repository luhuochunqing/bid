package com.xiyu.bid.file.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Builder;

/**
 * 申请 OBS 上传凭证请求。
 *
 * @param fileName      文件名
 * @param fileSize      文件大小（正数）
 * @param fileHash      整文件 MD5，可选，用于后续校验
 * @param businessType  业务类型，如 tender/project/knowledge
 * @param mimeType      MIME 类型，可选
 */
@Builder
public record UploadTokenRequest(
        @NotBlank(message = "文件名不能为空") String fileName,
        @NotNull(message = "文件大小不能为空") @Positive(message = "文件大小必须大于 0") Long fileSize,
        String fileHash,
        String businessType,
        String mimeType
) {
}
