// Input: 文档转换 sidecar 调用
// Output: 重试配置，sidecar 调用自动重试
// Pos: docinsight/infrastructure/config — Sidecar 熔断降级

package com.xiyu.bid.docinsight.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;

/**
 * Sidecar 调用重试配置。
 * <p>启用 {@code @Retryable} 注解处理，sidecar 临时不可用时自动重试。</p>
 */
@Configuration
@EnableRetry
public class SidecarRetryConfig {
}
