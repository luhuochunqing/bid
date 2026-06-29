package com.xiyu.bid.docinsight.infrastructure.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component("sidecar")
@Slf4j
public class SidecarHealthIndicator implements HealthIndicator {

    private final RestTemplate restTemplate;
    private final String sidecarUrl;

    public SidecarHealthIndicator(
            @Qualifier("markItDownSidecarRestTemplate") RestTemplate restTemplate,
            @Value("${app.doc-insight.sidecar-url:http://localhost:8000}") String sidecarUrl) {
        this.restTemplate = restTemplate;
        this.sidecarUrl = sidecarUrl;
    }

    @Override
    public Health health() {
        try {
            String response = restTemplate.getForObject(sidecarUrl + "/health", String.class);
            log.debug("Sidecar health check OK: {}", sidecarUrl);
            return Health.up()
                    .withDetail("url", sidecarUrl)
                    .withDetail("status", "reachable")
                    .withDetail("response", response != null && response.length() > 200
                            ? response.substring(0, 200) + "..."
                            : response)
                    .build();
        } catch (Exception e) {
            log.warn("Sidecar health check DOWN at {} ({}): {}",
                    sidecarUrl, e.getClass().getSimpleName(), e.getMessage());
            return Health.down()
                    .withDetail("url", sidecarUrl)
                    .withDetail("status", "unreachable")
                    .withDetail("errorType", e.getClass().getSimpleName())
                    .withDetail("errorMessage", e.getMessage())
                    .withDetail("fallbackAvailable", true)
                    .build();
        }
    }
}
