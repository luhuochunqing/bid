package com.xiyu.bid.file.dto;

import lombok.Builder;

import java.time.Instant;

/**
 * OBS 上传凭证响应。
 *
 * @param uploadId       上传 ID
 * @param accessKey      临时 AK
 * @param secretKey       临时 SK
 * @param securityToken  安全令牌
 * @param expiresAt      过期时间
 * @param bucket         OBS bucket
 * @param endpoint        OBS endpoint
 * @param region          OBS region
 * @param objectKey       对象 key
 */
@Builder
public record UploadTokenResponse(
        String uploadId,
        String accessKey,
        String secretKey,
        String securityToken,
        Instant expiresAt,
        String bucket,
        String endpoint,
        String region,
        String objectKey
) {
}
