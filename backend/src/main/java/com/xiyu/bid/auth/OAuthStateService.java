package com.xiyu.bid.auth;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service to manage OAuth2 state tokens for CSRF protection.
 * Uses Redis if available, otherwise falls back to in-memory map.
 */
@Service
@Slf4j
public class OAuthStateService {

    /** Redis template for distributed state storage. */
    private final StringRedisTemplate redisTemplate;

    /** Local fallback map for environments without Redis. */
    private final ConcurrentHashMap<String, Instant> localMap =
            new ConcurrentHashMap<>();

    /** Redis key prefix for OAuth states. */
    private static final String REDIS_PREFIX = "oauth_state:";

    /** Time-to-live for state tokens. */
    private static final Duration TTL = Duration.ofMinutes(10);

    /** Delay for local map cleanup task. */
    private static final long CLEAN_DELAY = 60000;

    /**
     * Constructor for OAuthStateService.
     *
     * @param redisTemplateParam Optional Redis template
     */
    public OAuthStateService(
            @Autowired(required = false)
            final StringRedisTemplate redisTemplateParam
    ) {
        this.redisTemplate = redisTemplateParam;
    }

    /**
     * Stores a state token with a 10-minute expiration.
     *
     * @param state CSRF state token to store
     */
    public void storeState(final String state) {
        if (redisTemplate != null) {
            try {
                redisTemplate.opsForValue().set(REDIS_PREFIX + state, "true",
                        TTL);
                return;
            } catch (Exception e) {
                log.warn("Failed to store OAuth state in Redis, falling back",
                        e);
            }
        }
        localMap.put(state, Instant.now());
    }

    /**
     * Validates and removes a state token.
     *
     * @param state CSRF state token to validate
     * @return true if state is valid and not expired
     */
    public boolean validateAndRemoveState(final String state) {
        if (state == null || state.isBlank()) {
            return false;
        }

        if (redisTemplate != null) {
            try {
                Boolean deleted = redisTemplate.delete(REDIS_PREFIX + state);
                return Boolean.TRUE.equals(deleted);
            } catch (Exception e) {
                log.warn("Failed to validate OAuth state, checking map",
                        e);
            }
        }

        Instant timestamp = localMap.remove(state);
        if (timestamp == null) {
            return false;
        }

        return Duration.between(timestamp, Instant.now())
                .compareTo(TTL) <= 0;
    }

    /**
     * Periodically clean expired states from local map.
     */
    @Scheduled(fixedDelay = CLEAN_DELAY)
    public void cleanExpiredLocalStates() {
        if (localMap.isEmpty()) {
            return;
        }

        Instant now = Instant.now();
        int removedCount = 0;

        var iterator = localMap.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (Duration.between(entry.getValue(), now).compareTo(TTL) > 0) {
                iterator.remove();
                removedCount++;
            }
        }

        if (removedCount > 0) {
            log.debug("Cleaned up {} expired local OAuth states", removedCount);
        }
    }
}
