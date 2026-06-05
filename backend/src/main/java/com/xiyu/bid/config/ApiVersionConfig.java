package com.xiyu.bid.config;

import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * API Version Configuration
 *
 * NOTE: Currently disabled to maintain backward compatibility with existing API paths.
 * To enable API versioning:
 * 1. Uncomment @Configuration annotation
 * 2. Update all client calls to use /api/v1/* paths
 * 3. Update all integration tests to use new paths
 *
 * Versioning Strategy: URI Path Versioning
 * - Current version: v1
 * - Future versions can be added as v2, v3, etc.
 * - Old versions can be deprecated gracefully
 *
 * @see org.springframework.web.servlet.config.annotation.WebMvcConfigurer
 */
// @Configuration // Uncomment to enable API versioning
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
        configurer.addPathPrefix(API_V1_PREFIX,
            c -> c.getPackage().getName().contains(".controller"));
    }
}
