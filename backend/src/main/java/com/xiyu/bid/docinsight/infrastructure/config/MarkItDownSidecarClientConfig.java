// Input: RestTemplateBuilder + 连接/读取超时配置（可通过 app.doc-insight.sidecar-*-timeout-ms 覆盖）
// Output: 专用于 MarkItDown Sidecar 调用的 RestTemplate bean（带显式超时），名称 markItDownSidecarRestTemplate
// Pos: docinsight/infrastructure/config — Sidecar HTTP 客户端配置，不依赖任何 Service
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

package com.xiyu.bid.docinsight.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * MarkItDown Sidecar 专用 RestTemplate 配置。
 * 默认值：连接 5s，读取 60s。共享的 RestTemplate 可能没有超时（Spring 默认无限），
 * 一个挂起的文档转换会阻塞整个 servlet 线程池，所以必须用独立 bean。
 * 配置前缀统一为 app.doc-insight.sidecar-*
 *
 * <p>显式使用 {@link SimpleClientHttpRequestFactory} 而非 RestTemplateBuilder 自动检测的
 * OkHttp3ClientHttpRequestFactory. 原因：openai-java-client-okhttp 传递依赖引入了 okhttp3,
 * RestTemplateBuilder 会自动用 OkHttp3, 但 OkHttp3 的 Request.Builder.method() 对 GET/HEAD
 * 严格要求 body 为 null, 而 LoggingClientHttpRequestInterceptor 传空 byte[] 会抛
 * IllegalArgumentException. Sidecar 健康检查和文档抽取都用 GET, 必须避开这个限制.
 * SimpleClientHttpRequestFactory 对 GET 空 body 处理正常.</p>
 */
@Configuration
public class MarkItDownSidecarClientConfig {

    @Bean(name = "markItDownSidecarRestTemplate")
    public RestTemplate markItDownSidecarRestTemplate(
            RestTemplateBuilder builder,
            @Value("${app.doc-insight.sidecar-connect-timeout-ms:5000}") long connectTimeoutMs,
            @Value("${app.doc-insight.sidecar-read-timeout-ms:60000}") long readTimeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) connectTimeoutMs);
        factory.setReadTimeout((int) readTimeoutMs);
        return builder
                .requestFactory(() -> factory)
                .build();
    }
}
