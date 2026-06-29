package com.xiyu.bid.ai.config;

import com.xiyu.bid.ai.client.AiProviderRuntimeConfig;
import com.xiyu.bid.ai.client.RoutingAiProvider;
import com.xiyu.bid.settings.service.AiConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("aiProvider")
@RequiredArgsConstructor
@Slf4j
public class AiProviderHealthIndicator implements HealthIndicator {

    private final RoutingAiProvider routingAiProvider;
    private final AiConfigService aiConfigService;

    @Override
    public Health health() {
        try {
            if (!aiConfigService.isAiEnabled()) {
                return Health.down()
                        .withDetail("status", "disabled")
                        .withDetail("reason", "AI 功能已在系统设置中关闭")
                        .build();
            }

            AiProviderRuntimeConfig config;
            try {
                config = routingAiProvider.resolveActiveConfig();
            } catch (Exception e) {
                log.warn("AI provider health check: config resolution failed - {}", e.getMessage());
                return Health.down()
                        .withDetail("status", "misconfigured")
                        .withDetail("reason", e.getMessage())
                        .withDetail("fallback", "mockMode")
                        .build();
            }

            if (config == null) {
                return Health.down()
                        .withDetail("status", "mockMode")
                        .withDetail("reason", "AI provider 配置为 mock，未使用真实 AI 服务")
                        .build();
            }

            return Health.up()
                    .withDetail("status", "configured")
                    .withDetail("provider", config.providerCode())
                    .withDetail("model", config.model())
                    .withDetail("baseUrl", maskUrl(config.baseUrl()))
                    .withDetail("apiKeyConfigured", config.apiKey() != null && !config.apiKey().isBlank())
                    .build();

        } catch (Exception e) {
            log.error("AI provider health check failed with exception", e);
            return Health.unknown()
                    .withDetail("error", e.getClass().getSimpleName())
                    .withDetail("message", e.getMessage())
                    .build();
        }
    }

    private String maskUrl(String url) {
        if (url == null || url.isBlank()) return "";
        try {
            java.net.URI uri = java.net.URI.create(url);
            String host = uri.getHost();
            if (host == null) return url;
            return uri.getScheme() + "://" + host + (uri.getPort() > 0 ? ":" + uri.getPort() : "") + "/...";
        } catch (Exception e) {
            return "[invalid-url]";
        }
    }
}
