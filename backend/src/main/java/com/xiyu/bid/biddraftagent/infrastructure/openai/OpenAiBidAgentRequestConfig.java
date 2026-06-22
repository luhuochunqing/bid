package com.xiyu.bid.biddraftagent.infrastructure.openai;

import java.time.Duration;

record OpenAiBidAgentRequestConfig(
        String apiKey,
        String baseUrl,
        String model,
        Duration timeout,
        OpenAiBidAgentApiStyle apiStyle,
        // 豆包使用 /v3 前缀，SDK 会自动拼接 /v1/chat/completions，需要手动指定完整 endpoint
        String fullEndpoint
) {
    public OpenAiBidAgentRequestConfig {
        if (fullEndpoint == null) {
            fullEndpoint = "";
        }
    }

    public OpenAiBidAgentRequestConfig(String apiKey, String baseUrl, String model, Duration timeout, OpenAiBidAgentApiStyle apiStyle) {
        this(apiKey, baseUrl, model, timeout, apiStyle, "");
    }
}
