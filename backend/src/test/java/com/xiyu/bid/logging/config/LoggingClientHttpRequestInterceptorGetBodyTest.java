package com.xiyu.bid.logging.config;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 markItDownSidecarRestTemplate 配置的 RestTemplate 对 GET 请求正常工作.
 *
 * <p>背景：项目通过 openai-java-client-okhttp 传递依赖引入了 okhttp3,
 * RestTemplateBuilder 默认会自动检测到 OkHttp3 并使用 OkHttp3ClientHttpRequestFactory.
 * 但 OkHttp3 的 Request.Builder.method() 对 GET/HEAD 严格要求 body 为 null,
 * 而 LoggingClientHttpRequestInterceptor 传空 byte[] 会抛
 * IllegalArgumentException: "method GET must not have a request body".
 * 这导致 SidecarHealthIndicator 和 MarkItDownSidecarExtractor 的
 * restTemplate.getForObject(.../health) 全部失败.</p>
 *
 * <p>修复：MarkItDownSidecarClientConfig 显式指定 SimpleClientHttpRequestFactory,
 * 避开 OkHttp3 的限制. 本测试验证修复后的 RestTemplate 对 GET 请求正常工作.</p>
 */
class LoggingClientHttpRequestInterceptorGetBodyTest {

    private MockWebServer server;
    private RestTemplate restTemplate;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        server.enqueue(new MockResponse().setBody("OK").setResponseCode(200));
        server.enqueue(new MockResponse().setBody("OK").setResponseCode(200));
        // 与修复后的 MarkItDownSidecarClientConfig 生产配置一致：
        // 显式 SimpleClientHttpRequestFactory + BufferingClientHttpRequestFactory + 日志拦截器
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) 5000);
        factory.setReadTimeout((int) 60000);
        restTemplate = new RestTemplateBuilder()
                .requestFactory(() -> factory)
                .build();
        restTemplate.setRequestFactory(new BufferingClientHttpRequestFactory(restTemplate.getRequestFactory()));
        restTemplate.getInterceptors().add(new LoggingClientHttpRequestInterceptor());
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    void getRequestShouldNotThrowIllegalArgumentException() throws Exception {
        // 修复前（用 OkHttp3）：抛 IllegalArgumentException("method GET must not have a request body")
        // 修复后（用 SimpleClientHttpRequestFactory）：正常返回
        String result = restTemplate.getForObject(server.url("/health").toString(), String.class);
        assertThat(result).isEqualTo("OK");
        assertThat(server.takeRequest().getMethod()).isEqualTo("GET");
    }

    @Test
    void secondGetAlsoWorks() {
        // 验证多次调用都正常（拦截器是无状态的）
        String result = restTemplate.getForObject(server.url("/health").toString(), String.class);
        assertThat(result).isEqualTo("OK");
    }
}
