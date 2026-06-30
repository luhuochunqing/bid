package com.xiyu.bid.docinsight.infrastructure.config;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SidecarHealthIndicator 单元测试.
 * 用 MockWebServer 模拟 sidecar /health 端点, 验证健康检查逻辑:
 * - 可达时返回 UP
 * - 不可达时返回 DOWN (不抛异常, 由 HealthIndicator 框架捕获)
 * - 长响应被截断到 200 字符
 *
 * <p>注意: RestTemplate 必须显式用 SimpleClientHttpRequestFactory, 与生产配置
 * (MarkItDownSidecarClientConfig) 一致. 否则 classpath 上的 okhttp3 会被
 * RestTemplateBuilder 自动检测, 导致 GET 请求带空 body 抛 IllegalArgumentException.</p>
 */
class SidecarHealthIndicatorTest {

    private MockWebServer server;
    private RestTemplate restTemplate;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        // 与 MarkItDownSidecarClientConfig 生产配置一致: 显式 SimpleClientHttpRequestFactory
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(60000);
        restTemplate = new RestTemplateBuilder()
                .requestFactory(() -> factory)
                .build();
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void shouldReturnUpWhenSidecarReachable() {
        server.enqueue(new MockResponse().setBody("{\"status\":\"UP\"}").setResponseCode(200));
        SidecarHealthIndicator indicator = new SidecarHealthIndicator(
                restTemplate, server.url("/").toString());

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("status", "reachable");
        assertThat(health.getDetails()).containsKey("url");
        assertThat(health.getDetails()).containsEntry("response", "{\"status\":\"UP\"}");
    }

    @Test
    void shouldReturnDownWhenConnectionFails() {
        // 用一个未监听的端口触发连接失败
        SidecarHealthIndicator indicator = new SidecarHealthIndicator(
                restTemplate, "http://127.0.0.1:1");

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("status", "unreachable");
        assertThat(health.getDetails()).containsEntry("url", "http://127.0.0.1:1");
        assertThat(health.getDetails()).containsEntry("fallbackAvailable", true);
        assertThat(health.getDetails()).containsKey("errorType");
        assertThat(health.getDetails()).containsKey("errorMessage");
    }

    @Test
    void shouldReturnDownWhenSidecarReturns500() {
        server.enqueue(new MockResponse().setBody("internal error").setResponseCode(500));
        SidecarHealthIndicator indicator = new SidecarHealthIndicator(
                restTemplate, server.url("/").toString());

        Health health = indicator.health();

        // RestTemplate 对 5xx 抛 HttpClientErrorException/HttpServerErrorException,
        // HealthIndicator 捕获后返回 DOWN
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("status", "unreachable");
        assertThat(health.getDetails()).containsEntry("fallbackAvailable", true);
    }

    @Test
    void shouldTruncateLongResponse() {
        // 构造 300 字符的响应, 验证截断到 200 + "..."
        String longBody = "x".repeat(300);
        server.enqueue(new MockResponse().setBody(longBody).setResponseCode(200));
        SidecarHealthIndicator indicator = new SidecarHealthIndicator(
                restTemplate, server.url("/").toString());

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        String response = (String) health.getDetails().get("response");
        assertThat(response).hasSize(203); // 200 + "..."
        assertThat(response).endsWith("...");
        assertThat(response).startsWith("xxxx");
    }
}
