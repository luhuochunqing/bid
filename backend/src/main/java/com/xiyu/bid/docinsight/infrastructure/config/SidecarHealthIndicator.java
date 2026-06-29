package com.xiyu.bid.docinsight.infrastructure.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Sidecar 健康检查指标.
 *
 * <p>使用 JDK 自带的 {@link HttpClient} 而非注入的 {@code markItDownSidecarRestTemplate}，
 * 原因：RestTemplate 的 {@code LoggingClientHttpRequestInterceptor} 会对所有请求（包括 GET）
 * 传空 body，导致 {@code SimpleClientHttpRequestFactory} 设置 {@code Content-Length: 0}，
 * 被 sidecar (uvicorn) 拒绝并报 "method GET must not have a request body"。
 * 健康检查只需简单 GET，无需日志拦截/超时定制，直接用 HttpClient 最干净。</p>
 */
@Component("sidecar")
@Slf4j
public class SidecarHealthIndicator implements HealthIndicator {

    private final String sidecarUrl;
    private final HttpClient httpClient;

    public SidecarHealthIndicator(
            @Value("${app.doc-insight.sidecar-url:http://localhost:8000}") String sidecarUrl) {
        this.sidecarUrl = sidecarUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
    }

    @Override
    public Health health() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(sidecarUrl + "/health"))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            String body = response.body();
            if (status >= 200 && status < 300) {
                log.debug("Sidecar health check OK: {} (status={})", sidecarUrl, status);
                return Health.up()
                        .withDetail("url", sidecarUrl)
                        .withDetail("status", "reachable")
                        .withDetail("httpStatus", status)
                        .withDetail("response", body != null && body.length() > 200
                                ? body.substring(0, 200) + "..."
                                : body)
                        .build();
            }
            log.warn("Sidecar health check DOWN at {} (non-2xx status={})", sidecarUrl, status);
            return Health.down()
                    .withDetail("url", sidecarUrl)
                    .withDetail("status", "unreachable")
                    .withDetail("httpStatus", status)
                    .withDetail("fallbackAvailable", true)
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
