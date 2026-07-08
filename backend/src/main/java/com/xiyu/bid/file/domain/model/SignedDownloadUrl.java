package com.xiyu.bid.file.domain.model;

import java.time.Instant;

/**
 * OBS 预签名下载 URL（domain 值对象）。
 *
 * <p>由 {@code ObsDownloadUrlGateway} 生成，application 层据此构造 DTO 响应，
 * 避免 application 直接依赖 OBS SDK。</p>
 *
 * @param url       预签名下载 URL
 * @param expiresAt 过期时间
 */
public record SignedDownloadUrl(
        String url,
        Instant expiresAt) {
}
