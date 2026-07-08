package com.xiyu.bid.crm.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Instant;
import java.util.Base64;

/**
 * JWT TTL 解析工具（CO-501 修复）。
 *
 * <p>从 JWT 的 {@code exp} claim 计算剩余缓存 TTL，避免缓存比 JWT 本身后过期。
 */
public final class JwtTtlResolver {

    private static final Logger log = LoggerFactory.getLogger(JwtTtlResolver.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final long DEFAULT_TTL_FALLBACK_SECONDS = 86400L;
    private static final long SAFETY_MARGIN_SECONDS = 300;

    private JwtTtlResolver() { /* utility */ }

    /**
     * 解析 JWT 的 exp claim，计算剩余 TTL（秒）。
     *
     * @param jwtToken CRM JWT token
     * @return 剩余 TTL（秒），留 5 分钟安全余量；解析失败回退到 86400（24h）
     */
    public static long resolveTtlSeconds(String jwtToken) {
        try {
            String[] parts = jwtToken.split("\\.");
            if (parts.length < 2) return DEFAULT_TTL_FALLBACK_SECONDS;
            String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
            JsonNode node = MAPPER.readTree(payload);
            if (node.has("exp")) {
                long expEpochSeconds = node.get("exp").asLong();
                long nowEpochSeconds = Instant.now().getEpochSecond();
                long ttl = expEpochSeconds - nowEpochSeconds - SAFETY_MARGIN_SECONDS;
                if (ttl > 0) return ttl;
            }
        } catch (IllegalArgumentException | IOException e) {
            log.warn("Failed to parse JWT exp claim for TTL, falling back to {}s: {}",
                    DEFAULT_TTL_FALLBACK_SECONDS, e.getMessage());
        }
        return DEFAULT_TTL_FALLBACK_SECONDS;
    }
}
