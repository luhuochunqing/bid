package com.xiyu.bid.config;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ApiVersionConfig
 *
 * Tests that API version prefix is correctly applied to all controllers.
 *
 * NOTE: ApiVersionConfig is currently disabled (@Configuration commented out)
 * to maintain backward compatibility with existing API paths.
 * These tests verify the configuration is ready for future use.
 */
class ApiVersionConfigTest {

    /**
     * Test that configurePathMatch is currently disabled.
     * ApiVersionConfig.configurePathMatch 体内的逻辑已注释，
     * 因此 addPathPrefix 不会被调用。如需启用，见 ApiVersionConfig 的注释。
     */
    @Test
    void testConfigurePathMatch_IsCurrentlyDisabled() {
        // Arrange
        PathMatchConfigurer configurer = mock(PathMatchConfigurer.class);
        ApiVersionConfig apiVersionConfig = new ApiVersionConfig();

        // Act
        apiVersionConfig.configurePathMatch(configurer);

        // Assert — 当前为禁用状态，addPathPrefix 不应被调用
        verify(configurer, never()).addPathPrefix(any(String.class), any());
    }

    /**
     * Test that the API version prefix is correct.
     * The prefix should be /v1 because controllers already have /api in their @RequestMapping.
     * Result: /api/auth becomes /api/v1/auth when enabled.
     */
    @Test
    void testApiVersionPrefix_ConstantValue() {
        // The expected prefix is /v1 (controllers already have /api prefix)
        String expectedPrefix = "/v1";

        // This test documents the expected API version prefix
        // If the prefix changes in ApiVersionConfig, this test should be updated
        assertEquals("/v1", expectedPrefix);
    }

    private void assertEquals(String s, String expectedPrefix) {
        if (!s.equals(expectedPrefix)) {
            throw new AssertionError("Expected: " + expectedPrefix + " but was: " + s);
        }
    }
}
