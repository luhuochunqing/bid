package com.xiyu.bid.file.dto;

import lombok.Builder;

import java.time.Instant;

/**
 * 预签名下载 URL 响应。
 *
 * @param url       预签名下载 URL
 * @param expiresAt 过期时间
 */
@Builder
public record DownloadUrlResponse(
        String url,
        Instant expiresAt
) {
}
