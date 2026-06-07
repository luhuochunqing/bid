// Input: Spring 配置属性、环境变量、外部 bean 依赖
// Output: 配置 Bean、过滤器、线程池和启动级常量
// Pos: Config/基础设施层
// 维护声明: 仅维护配置与启动约束；业务规则变更请同步到对应 service/controller.
package com.xiyu.bid.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate limiting configuration using Redis for distributed rate limiting
 */
@Configuration
public class RateLimitConfig {

    @Bean
    public RedisTemplate<String, String> redisTemplate(org.springframework.beans.factory.ObjectProvider<RedisConnectionFactory> connectionFactoryProvider) {
        RedisConnectionFactory connectionFactory = connectionFactoryProvider.getIfAvailable();
        if (connectionFactory == null) {
            return null;
        }
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        return template;
    }

    @Bean
    public RateLimiter rateLimiter(org.springframework.beans.factory.ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
        return new RateLimiter(redisTemplateProvider.getIfAvailable());
    }

    /**
     * Rate limiter using Redis for distributed rate limiting
     * Falls back to in-memory rate limiting if Redis is unavailable
     */
    public static class RateLimiter {
        private final StringRedisTemplate redisTemplate;
        private final ConcurrentHashMap<String, RateLimitInfo> localCache = new ConcurrentHashMap<>();

        public RateLimiter(StringRedisTemplate pRedisTemplate) {
            this.redisTemplate = pRedisTemplate;
        }

        /**
         * Check if the request should be rate limited
         * @param key Unique identifier for the rate limit (e.g., IP address, username)
         * @param maxRequests Maximum number of requests allowed
         * @param duration Time window for the rate limit
         * @return true if the request should be allowed, false if rate limited
         */
        public boolean allowRequest(String key, int maxRequests, Duration duration) {
            try {
                String redisKey = "rate_limit:" + key;
                Long currentCount = redisTemplate.opsForValue().increment(redisKey);

                if (currentCount == null) {
                    // Fallback to local cache if Redis fails
                    return allowRequestLocal(key, maxRequests, duration);
                }

                if (currentCount == 1) {
                    // First request, set expiration
                    redisTemplate.expire(redisKey, duration);
                }

                return currentCount <= maxRequests;
            } catch (RuntimeException e) {
                // Redis unavailable, fallback to local cache
                return allowRequestLocal(key, maxRequests, duration);
            }
        }

        private boolean allowRequestLocal(String key, int maxRequests, Duration duration) {
            RateLimitInfo info = localCache.compute(key, (k, existing) -> {
                long now = System.currentTimeMillis();
                if (existing == null || now > existing.getExpiryTime()) {
                    return new RateLimitInfo(1, now + duration.toMillis());
                }
                return new RateLimitInfo(existing.getCount() + 1, existing.getExpiryTime());
            });
            return info.getCount() <= maxRequests;
        }

        private static class RateLimitInfo {
            private final long count;
            private final long expiryTime;

            RateLimitInfo(long pCount, long pExpiryTime) {
                this.count = pCount;
                this.expiryTime = pExpiryTime;
            }

            public long getCount() {
                return count;
            }

            public long getExpiryTime() {
                return expiryTime;
            }
        }
    }
}
