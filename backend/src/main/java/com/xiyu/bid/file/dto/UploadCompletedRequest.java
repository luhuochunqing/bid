package com.xiyu.bid.file.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * 前端通知 OBS 上传完成请求。
 */
@Getter
@Setter
@Builder
public class UploadCompletedRequest {

    @NotBlank(message = "objectKey 不能为空")
    private String objectKey;

    /** OBS 返回的 ETag */
    private String etag;

    @NotBlank(message = "bucket 不能为空")
    private String bucket;
}
