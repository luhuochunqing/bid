package com.xiyu.bid.file.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * 预签名下载 URL 响应。
 */
@Getter
@Setter
@Builder
public class DownloadUrlResponse {

    private String url;

    private Instant expiresAt;
}
