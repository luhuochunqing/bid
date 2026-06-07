package com.xiyu.bid.integration.application;

import com.xiyu.bid.integration.domain.WeComApiErrCode;
import com.xiyu.bid.integration.domain.WeComSendResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Sends WeCom application messages.
 * Single responsibility: message sending with one-time token-refresh retry on 40014.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WeComMessagePublisher {

    private final WeComAccessTokenProvider tokenProvider;
    private final WeComApiClient apiClient;

    /**
     * Sends a text message to the given user IDs.
     * On token expiry (errcode=40014): invalidates cache and retries once.
     *
     * @throws IllegalArgumentException if toUserIds is null or empty
     */
    public WeComSendResult sendTextMessage(
            String corpId,
            String agentId,
            String corpSecret,
            List<String> toUserIds,
            String content
    ) {
        if (toUserIds == null || toUserIds.isEmpty()) {
            throw new IllegalArgumentException("toUserIds must not be null or empty");
        }

        String token = tokenProvider.getAccessToken(corpId, agentId, corpSecret);
        Map<String, Object> payload = buildPayload(agentId, toUserIds, content);

        WeComApiClient.WeComSendResponse response = apiClient.sendAppMessage(token, payload);

        if (response.errcode() == WeComApiErrCode.INVALID_TOKEN.code()) {
            log.warn("WeCom sendmessage got 40014 (invalid token), invalidating and retrying once");
            tokenProvider.invalidate(corpId, agentId);
            String freshToken = tokenProvider.getAccessToken(corpId, agentId, corpSecret);
            response = apiClient.sendAppMessage(freshToken, payload);
        }

        boolean success = response.errcode() == WeComApiErrCode.OK.code();
        return new WeComSendResult(success, response.errcode(), response.errmsg(), toUserIds);
    }

    private Map<String, Object> buildPayload(String agentId, List<String> toUserIds, String content) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("touser", String.join("|", toUserIds));
        payload.put("msgtype", "text");
        payload.put("agentid", Integer.parseInt(agentId));
        payload.put("text", Map.of("content", content));
        payload.put("safe", 0);
        return payload;
    }
}
