package com.xiyu.bid.file.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * OBS 上传凭证响应。
 */
@Getter
@Setter
@Builder
public class UploadTokenResponse {

    private String uploadId;

    private String accessKey;

    private String secretKey;

    private String securityToken;

    private Instant expiresAt;

    private String bucket;

    private String endpoint;

    private String region;

    private String objectKey;
}
