package com.xiyu.bid.file.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * 申请 OBS 上传凭证请求。
 */
@Getter
@Setter
@Builder
public class UploadTokenRequest {

    @NotBlank(message = "文件名不能为空")
    private String fileName;

    @NotNull(message = "文件大小不能为空")
    @Positive(message = "文件大小必须大于 0")
    private Long fileSize;

    /** 整文件 MD5，可选，用于后续校验 */
    private String fileHash;

    /** 业务类型，如 tender/project/knowledge */
    private String businessType;

    /** MIME 类型，可选 */
    private String mimeType;
}
