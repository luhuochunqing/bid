// Input: Spring environment and framework beans
// Output: Cache configuration beans
// Pos: Config/配置层
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.analytics.config;

import java.time.Duration;

import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;

/**
 * Cache configuration for dashboard analytics.
 *
 * <p>Cache manager 由 Spring Boot Redis 自动配置提供，全局默认 TTL 通过
 * {@code spring.cache.redis.time-to-live}（application.yml）控制。
 *
 * <p>此处通过 {@link RedisCacheManagerBuilderCustomizer} 为关键 cacheName
 * 显式声明 TTL，使每个缓存的过期策略在配置层可见、可调，避免 TTL 隐式
 * 继承全局默认导致审查困难（L-08）。
 *
 * <h3>Cache registry</h3>
 * <ul>
 *   <li>{@code dashboard:overview} — 看板概览，TTL 5 分钟</li>
 *   <li>{@code users:enabled} — 启用用户列表（仅 @CacheEvict，TTL 仅作兜底）</li>
 * </ul>
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /**
     * 显式按 cacheName 配置 TTL，覆盖全局默认。
     *
     * <p>新增 cacheName 时在此登记 TTL，保持缓存注册表单一真相。
     */
    @Bean
    public RedisCacheManagerBuilderCustomizer redisCacheManagerBuilderCustomizer() {
        return builder -> builder
                .withCacheConfiguration("dashboard:overview",
                        RedisCacheConfiguration.defaultCacheConfig()
                                .entryTtl(Duration.ofMinutes(5)))
                .withCacheConfiguration("users:enabled",
                        RedisCacheConfiguration.defaultCacheConfig()
                                .entryTtl(Duration.ofMinutes(30)));
    }
}
