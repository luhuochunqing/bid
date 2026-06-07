package com.xiyu.bid.biddraftagent.infrastructure.openai;

import java.time.Duration;

record OpenAiBidAgentRequestConfig(
        String apiKey,
        String baseUrl,
        String model,
        Duration timeout,
        OpenAiBidAgentApiStyle apiStyle
) {
}
