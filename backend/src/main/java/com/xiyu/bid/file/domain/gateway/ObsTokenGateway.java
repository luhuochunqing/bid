package com.xiyu.bid.file.domain.gateway;

import com.xiyu.bid.file.domain.model.TemporaryCredentials;

/**
 * OBS 临时凭证签发端口（Hexagonal 入站端口）。
 *
 * <p>application 层依赖此接口而非 infra 实现类，避免 SDK 泄漏到 application 层。
 * 实现由 {@code com.xiyu.bid.file.infrastructure.obs} 提供。</p>
 */
public interface ObsTokenGateway {

    /**
     * 为指定上传 ID 签发临时访问凭证。
     *
     * @param uploadId 上传 ID
     * @return 临时凭证
     */
    TemporaryCredentials issueToken(String uploadId);
}
