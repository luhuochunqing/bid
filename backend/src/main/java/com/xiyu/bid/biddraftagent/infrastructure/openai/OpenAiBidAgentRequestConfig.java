package com.xiyu.bid.biddraftagent.infrastructure.openai;

import java.time.Duration;

public record OpenAiBidAgentRequestConfig(
        String apiKey,
        String baseUrl,
        String model,
        Duration timeout,
        OpenAiBidAgentApiStyle apiStyle
) {
}
