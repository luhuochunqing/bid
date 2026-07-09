package com.xiyu.bid.file.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

/**
 * 前端通知 OBS 上传完成请求。
 *
 * @param objectKey OBS 对象 key
 * @param etag      OBS 返回的 ETag
 * @param bucket    OBS bucket
 */
@Builder
public record UploadCompletedRequest(
        @NotBlank(message = "objectKey 不能为空") String objectKey,
        String etag,
        @NotBlank(message = "bucket 不能为空") String bucket
) {
}
