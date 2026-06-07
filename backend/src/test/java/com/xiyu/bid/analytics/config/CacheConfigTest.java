package com.xiyu.bid.analytics.config;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.cache.CacheAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import static org.assertj.core.api.Assertions.assertThat;

class CacheConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withPropertyValues(
                    "spring.cache.type=redis",
                    "spring.cache.redis.time-to-live=300000",
                    "spring.cache.redis.cache-null-values=false",
                    "spring.cache.redis.key-prefix=xiyu-bid:",
                    "spring.cache.redis.use-key-prefix=true"
            )
            .withBean(RedisConnectionFactory.class, () -> Mockito.mock(RedisConnectionFactory.class))
            .withUserConfiguration(CacheConfig.class)
            .withConfiguration(AutoConfigurations.of(CacheAutoConfiguration.class));

    @Test
    void shouldUseRedisCacheManagerWithoutLocalMapCacheManager() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(CacheManager.class);

            CacheManager cacheManager = context.getBean(CacheManager.class);
            assertThat(cacheManager).isInstanceOf(RedisCacheManager.class);
            assertThat(cacheManager).isNotInstanceOf(ConcurrentMapCacheManager.class);

            assertThat(cacheManager.getCache("dashboard:overview")).isNotNull();
            assertThat(cacheManager.getCache("dashboard:summary")).isNotNull();
            assertThat(cacheManager.getCache("dashboard:trends")).isNotNull();
        });
    }
}
