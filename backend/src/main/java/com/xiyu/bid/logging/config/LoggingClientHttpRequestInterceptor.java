package com.xiyu.bid.logging.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 外部 HTTP 调用日志拦截器
 * 拦截 RestTemplate 发出的请求，记录请求头、请求体、响应头、响应体
 */
public class LoggingClientHttpRequestInterceptor implements ClientHttpRequestInterceptor {

    private static final Logger log = LoggerFactory.getLogger("com.xiyu.bid.external.api");

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
        logRequest(request, body);
        long start = System.currentTimeMillis();
        ClientHttpResponse response = execution.execute(request, body);
        long elapsed = System.currentTimeMillis() - start;
        logResponse(request, response, elapsed);
        return response;
    }

    private void logRequest(HttpRequest request, byte[] body) {
        if (log.isDebugEnabled()) {
            String bodyString = new String(body, StandardCharsets.UTF_8);
            if (bodyString.length() > 2048) {
                bodyString = bodyString.substring(0, 2048) + "...(truncated)";
            }
            log.debug("EXTERNAL_REQ: URI={}, Method={}, Headers={}, Body={}",
                    request.getURI(), request.getMethod(), request.getHeaders(), bodyString);
        }
    }

    private void logResponse(HttpRequest request, ClientHttpResponse response, long elapsed) throws IOException {
        if (log.isDebugEnabled()) {
            // 注意：要让响应体可重复读取，需要配合 BufferingClientHttpRequestFactory
            // 否则这里读了，业务代码就读不到。
            String bodyString = "Body logging requires BufferingClientHttpRequestFactory";
            try {
                // 如果包装了，就能读。为了安全，这里只尝试，不强求。
                byte[] bytes = StreamUtils.copyToByteArray(response.getBody());
                bodyString = new String(bytes, StandardCharsets.UTF_8);
                if (bodyString.length() > 2048) {
                    bodyString = bodyString.substring(0, 2048) + "...(truncated)";
                }
            } catch (java.io.IOException e) {
                // ignore if stream is closed
            }
            log.debug("EXTERNAL_RES: URI={}, Status={}, Elapsed={}ms, Headers={}, Body={}",
                    request.getURI(), response.getStatusCode(), elapsed, response.getHeaders(), bodyString);
        }
    }
}
