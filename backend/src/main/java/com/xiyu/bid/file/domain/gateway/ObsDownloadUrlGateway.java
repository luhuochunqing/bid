package com.xiyu.bid.file.domain.gateway;

import com.xiyu.bid.file.domain.model.SignedDownloadUrl;

/**
 * OBS 预签名下载 URL 签发端口（Hexagonal 入站端口）。
 *
 * <p>application 层依赖此接口而非直接 new ObsClient，避免 SDK 泄漏到 application 层。
 * 实现由 {@code com.xiyu.bid.file.infrastructure.obs} 提供。</p>
 */
public interface ObsDownloadUrlGateway {

    /**
     * 生成预签名下载 URL。
     *
     * @param bucket        桶名
     * @param objectKey     对象键
     * @param expireSeconds  有效期（秒），调用方应已 clamp 到 [60, 3600]
     * @return 预签名下载 URL
     */
    SignedDownloadUrl signDownloadUrl(String bucket, String objectKey, int expireSeconds);
}
