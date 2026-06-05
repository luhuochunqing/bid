// Input: 进程内缓存条目
// Output: 短期幂等响应
// Pos: Idempotency/内存兜底
// 维护声明: e2e 与 Redis 缺席场景兜底；生产请用 RedisIdempotencyStore。
package com.xiyu.bid.idempotency;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component("inMemoryIdempotencyStore")
public class InMemoryIdempotencyStore implements IdempotencyStore {

    private static final int CLEANUP_THRESHOLD = 1024;

    private final Map<String, Entry> cache = new ConcurrentHashMap<>();

    @Override
    public Optional<CachedResponse> find(String key) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        Entry entry = cache.get(key);
        if (entry == null) {
            return Optional.empty();
        }
        if (entry.expiresAt.isBefore(Instant.now())) {
            cache.remove(key, entry);
            return Optional.empty();
        }
        return Optional.of(entry.response);
    }

    @Override
    public void save(String key, CachedResponse response, Duration ttl) {
        if (key == null || key.isBlank() || response == null) {
            return;
        }
        Duration effective = ttl == null || ttl.isZero() || ttl.isNegative() ? Duration.ofMinutes(10) : ttl;
        cache.put(key, new Entry(response, Instant.now().plus(effective)));
        if (cache.size() > CLEANUP_THRESHOLD) {
            cleanupExpired();
        }
    }

    private void cleanupExpired() {
        Instant now = Instant.now();
        cache.entrySet().removeIf(entry -> entry.getValue().expiresAt.isBefore(now));
    }

    private static final class Entry {
        private final CachedResponse response;
        private final Instant expiresAt;

        Entry(CachedResponse response, Instant expiresAt) {
            this.response = response;
            this.expiresAt = expiresAt;
        }
    }
}
