// Input: RestTemplateBuilder + 连接/读取超时配置（可通过 app.converter.sidecar-*-timeout-ms 覆盖）
// Output: 专用于 MarkItDown Sidecar 调用的 RestTemplate bean（带显式超时），名称 markItDownSidecarRestTemplate
// Pos: biddraftagent/infrastructure/tenderdocument — Sidecar HTTP 客户端配置，不依赖任何 Service
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

package com.xiyu.bid.docinsight.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * MarkItDown Sidecar 专用 RestTemplate 配置。
 * 默认值：连接 5s，读取 60s。共享的 RestTemplate 可能没有超时（Spring 默认无限），
 * 一个挂起的文档转换会阻塞整个 servlet 线程池，所以必须用独立 bean。
 */
@Configuration
public class MarkItDownSidecarClientConfig {

    @Bean(name = "markItDownSidecarRestTemplate")
    public RestTemplate markItDownSidecarRestTemplate(
            RestTemplateBuilder builder,
            @Value("${app.converter.sidecar-connect-timeout-ms:5000}") long connectTimeoutMs,
            @Value("${app.converter.sidecar-read-timeout-ms:60000}") long readTimeoutMs) {
        return builder
                .setConnectTimeout(Duration.ofMillis(connectTimeoutMs))
                .setReadTimeout(Duration.ofMillis(readTimeoutMs))
                .build();
    }
}
