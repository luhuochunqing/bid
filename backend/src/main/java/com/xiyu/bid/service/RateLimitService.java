// Input: Redis 连接、限流配置和请求标识
// Output: 限流判断与计数结果
// Pos: Service/基础设施支撑层
// 维护声明: 仅维护限流实现；登录策略调整请同步 SecurityConfig 与 Filter.
package com.xiyu.bid.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Rate limiting service for export operations.
 * Uses Redis to track export counts per user.
 */
@Service
@Slf4j
public class RateLimitService {

    private StringRedisTemplate redisTemplate;
    private final int maxExportsPerHour;

    private static final String EXPORT_RATE_LIMIT_KEY_PREFIX = "export:rateLimit:user:";

    public RateLimitService(@Value("${app.export.max-exports-per-hour:10}") int pMaxExportsPerHour) {
        this.redisTemplate = null;
        this.maxExportsPerHour = pMaxExportsPerHour;
    }

    @Autowired(required = false)
    public void setRedisTemplate(StringRedisTemplate pRedisTemplate) {
        this.redisTemplate = pRedisTemplate;
        if (pRedisTemplate != null) {
            log.info("Rate limit service wired with Redis template");
        } else {
            log.info("Rate limit service running without Redis template");
        }
    }

    /**
     * Check if the user has exceeded their export rate limit.
     *
     * @param userId The ID of the user to check
     * @return true if the user is within their limit, false if they have exceeded it
     */
    public boolean checkExportRateLimit(Long userId) {
        if (userId == null) {
            // If we can't identify the user, allow the request but log it
            log.warn("Rate limit check: userId is null, allowing request");
            return true;
        }
        if (redisTemplate == null) {
            log.debug("Rate limit check: Redis unavailable, allowing request for user {}", userId);
            return true;
        }

        try {
            String key = EXPORT_RATE_LIMIT_KEY_PREFIX + userId;

            // Get current count
            Long currentCount = redisTemplate.opsForValue().increment(key);

            if (currentCount == null) {
                // If increment failed, assume Redis is down and allow
                log.warn("Rate limit check: Redis increment failed, allowing request");
                return true;
            }

            // Set expiration on first increment (1 hour)
            if (currentCount == 1) {
                redisTemplate.expire(key, Duration.ofHours(1));
            }

            // Check if limit exceeded
            if (currentCount > maxExportsPerHour) {
                log.info("Rate limit exceeded: userId={}, count={}, limit={}",
                    userId, currentCount, maxExportsPerHour);
                return false;
            }

            return true;

        } catch (RuntimeException e) {
            // If Redis is unavailable, fail open (allow the request)
            log.error("Rate limit check failed for user {}: {}", userId, e.getMessage());
            return true;
        }
    }

    /**
     * Reset the rate limit for a specific user.
     * For admin use only.
     *
     * @param userId The ID of the user whose limit should be reset
     */
    public void resetRateLimit(Long userId) {
        if (userId == null || redisTemplate == null) {
            return;
        }

        try {
            String key = EXPORT_RATE_LIMIT_KEY_PREFIX + userId;
            redisTemplate.delete(key);
            log.info("Rate limit reset for user: {}", userId);
        } catch (RuntimeException e) {
            log.error("Failed to reset rate limit for user {}: {}", userId, e.getMessage());
        }
    }

    /**
     * Get the current export count for a user.
     *
     * @param userId The ID of the user
     * @return The number of exports in the current time window
     */
    public long getCurrentExportCount(Long userId) {
        if (userId == null || redisTemplate == null) {
            return 0;
        }

        try {
            String key = EXPORT_RATE_LIMIT_KEY_PREFIX + userId;
            String count = redisTemplate.opsForValue().get(key);
            return count != null ? Long.parseLong(count.toString()) : 0;
        } catch (RuntimeException e) {
            log.error("Failed to get export count for user {}: {}", userId, e.getMessage());
            return 0;
        }
    }
}
