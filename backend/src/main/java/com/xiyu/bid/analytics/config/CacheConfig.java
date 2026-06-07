// Input: Spring environment and framework beans
// Output: Cache configuration beans
// Pos: Config/配置层
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.analytics.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

/**
 * Cache configuration for dashboard analytics.
 * Cache management is delegated to Spring Boot Redis auto-configuration.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    // Cache manager is provided by Spring Boot Redis auto-configuration.
}
