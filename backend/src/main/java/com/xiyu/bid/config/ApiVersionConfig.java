package com.xiyu.bid.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.context.annotation.Configuration;

/**
 * API Version Configuration
 *
 * NOTE: Enabled. /api/v1/* 路径由 ApiVersionFilter 重写为 /api/* 兼容。
 * To enable API versioning:
 * 1. （已启用）
 * 2. 前端逐步迁移到 /api/v1/*
 * 3. 集成测试逐步迁移到 /api/v1/*
 *
 * Versioning Strategy: URI Path Versioning
 * - Current version: v1
 * - Future versions can be added as v2, v3, etc.
 * - Old versions can be deprecated gracefully
 *
 * @see org.springframework.web.servlet.config.annotation.WebMvcConfigurer
 */
@Configuration
public class ApiVersionConfig implements WebMvcConfigurer {

    /**
     * API version prefix for v1 endpoints
     * Note: Controllers already have /api prefix, so we only add /v1
     */
    private static final String API_V1_PREFIX = "/v1";

    /**
     * Configure path matching to add version prefix to all controllers.
     *
     * The predicate c -> true applies the prefix to ALL controllers.
     * Result: /api/auth becomes /api/v1/auth
     *
     * To selectively apply to specific controllers, modify the predicate:
     * - c -> c.getPackage().getName().contains(".controller.") - all controllers
     * - c -> c.getClass().isAnnotationPresent(RestController.class) - REST controllers
     *
     * @param configurer the path match configurer
     */
    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        // NOTE: 暂时禁用 /v1 路径前缀注入，前端仍使用 /api/*。
        // 如需启用，请同步更新前端 API base URL 为 /api/v1。
        // configurer.addPathPrefix(API_V1_PREFIX,
        //     c -> c.getPackage().getName().contains(".controller"));
    }
}
