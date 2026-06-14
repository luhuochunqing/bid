// Input: jti、过期时间
// Output: 进程内的撤销标记
// Pos: Auth/Token 撤销层
// 维护声明: e2e 与 Redis 缺席环境兜底；生产请用 RedisTokenRevocationService.
package com.xiyu.bid.auth;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service("inMemoryTokenRevocationService")
@ConditionalOnProperty(
        name = "app.auth.token-revocation.store",
        havingValue = "in-memory",
        matchIfMissing = false)
public class InMemoryTokenRevocationService implements TokenRevocationService {

    private static final int CLEANUP_THRESHOLD = 1024;

    private final Map<String, Instant> revoked = new ConcurrentHashMap<>();

    @Override
    public void revoke(String jti, Instant expiresAt) {
        if (jti == null || jti.isBlank()) {
            return;
        }
        Instant cutoff = expiresAt != null ? expiresAt : Instant.now().plusSeconds(24L * 3600L);
        if (cutoff.isAfter(Instant.now())) {
            revoked.put(jti, cutoff);
        }
        if (revoked.size() > CLEANUP_THRESHOLD) {
            cleanupExpired();
        }
    }

    @Override
    public boolean isRevoked(String jti) {
        if (jti == null || jti.isBlank()) {
            return false;
        }
        Instant expiresAt = revoked.get(jti);
        if (expiresAt == null) {
            return false;
        }
        if (expiresAt.isBefore(Instant.now())) {
            revoked.remove(jti, expiresAt);
            return false;
        }
        return true;
    }

    private void cleanupExpired() {
        Instant now = Instant.now();
        revoked.entrySet().removeIf(entry -> entry.getValue().isBefore(now));
    }
}
