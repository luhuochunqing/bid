package com.xiyu.bid.wecom.infrastructure;

import com.fasterxml.jackson.annotation.JsonInclude;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.wecom.config.WecomMessageCenterProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * 西域统一消息中心「企微消息」HTTP 客户端。
 *
 * <p>请求路径：{@code POST {baseUrl}/qywx/sendMSG}
 * 请求体：{@code { "userName": "工号", "message": "...", "code": "应用code", "email": "..." }}
 */
@Component
@Slf4j
public class WecomMessageCenterClient {

    /**
     * 消息中心响应。
     *
     * @param code    消息中心返回 code（成功时通常为 0）
     * @param message 返回消息
     * @param trace   追踪号
     */
    public record MessageCenterResponse(int code, String message, String trace) {
    }

    private final RestTemplate restTemplate;
    private final WecomMessageCenterProperties properties;
    private final ObjectMapper objectMapper;

    public WecomMessageCenterClient(
            RestTemplateBuilder restTemplateBuilder,
            WecomMessageCenterProperties properties) {
        this.properties = properties;
        this.objectMapper = new ObjectMapper();
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()))
                .setReadTimeout(Duration.ofMillis(properties.getReadTimeoutMs()))
                .build();
    }

    /**
     * 发送企微消息。
     *
     * @param userName 接收人工号
     * @param message  消息内容
     * @return 消息中心响应
     */
    public MessageCenterResponse sendMessage(String userName, String message) {
        return sendMessage(userName, message, null);
    }

    public MessageCenterResponse sendMessage(String userName, String message, String email) {
        String url = properties.sendUrl();
        String code = properties.getApplicationCode();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        var body = new SendMessageRequest(userName, message, code, email == null || email.isBlank() ? null : email.trim());
        HttpEntity<SendMessageRequest> request = new HttpEntity<>(body, headers);

        try {
            log.debug("Sending WeCom message via message-center: url={}, userName={}", url, userName);
            var response = restTemplate.exchange(url, HttpMethod.POST, request, String.class);
            MessageCenterResponse result = parseResponse(response.getBody());
            if (result.code() != 0) {
                log.warn("Message-center business failure: userName={}, code={}, message={}, trace={}",
                        userName, result.code(), result.message(), result.trace());
            }
            return result;
        } catch (HttpStatusCodeException ex) {
            log.warn("Message-center send HTTP error: {}", ex.getStatusCode());
            throw new WecomMessageCenterException(
                    "Message-center send HTTP error: " + ex.getStatusCode(), ex);
        } catch (RestClientException ex) {
            log.warn("Message-center send request failed: {}", ex.getMessage());
            throw new WecomMessageCenterException(
                    "Message-center send request failed: " + ex.getMessage(), ex);
        }
    }

    private MessageCenterResponse parseResponse(String rawBody) {
        if (rawBody == null || rawBody.isBlank()) {
            throw new WecomMessageCenterException("Message-center returned empty body");
        }
        try {
            JsonNode root = objectMapper.readTree(rawBody);
            int code = root.path("code").asInt(-1);
            String message = root.path("message").asText("");
            String trace = root.path("trace").asText("");
            return new MessageCenterResponse(code, message, trace);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new WecomMessageCenterException("Failed to parse message-center response: " + rawBody, e);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record SendMessageRequest(String userName, String message, String code, String email) {
    }
}
