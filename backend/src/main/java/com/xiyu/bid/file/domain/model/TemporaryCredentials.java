package com.xiyu.bid.file.domain.model;

import java.time.Instant;

/**
 * OBS 临时访问凭证（domain 值对象）。
 *
 * <p>从 {@code HuaweiObsTokenService} 内嵌 record 抽出到 domain 层，
 * 供 gateway 端口接口与 application 层共享，避免 application 直接依赖 infra 实现类。</p>
 *
 * @param accessKey      临时 AK
 * @param secretKey      临时 SK
 * @param securityToken  安全令牌（AK/SK 直传模式时为 null）
 * @param expiresAt      过期时间
 */
public record TemporaryCredentials(
        String accessKey,
        String secretKey,
        String securityToken,
        Instant expiresAt) {
}
