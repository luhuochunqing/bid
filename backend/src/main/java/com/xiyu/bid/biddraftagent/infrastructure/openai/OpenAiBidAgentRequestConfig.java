// Input: resolved provider credentials and per-use-case request budgets
// Output: immutable SDK request configuration; legacy callers retain two retries
// Pos: biddraftagent infrastructure request contract
package com.xiyu.bid.biddraftagent.infrastructure.openai;

import java.time.Duration;

public record OpenAiBidAgentRequestConfig(
        String apiKey, String baseUrl, String model, Duration timeout,
        OpenAiBidAgentApiStyle apiStyle, int maxRetries
) {
    public OpenAiBidAgentRequestConfig(String apiKey, String baseUrl, String model,
                                      Duration timeout, OpenAiBidAgentApiStyle apiStyle) {
        this(apiKey, baseUrl, model, timeout, apiStyle, 2);
    }
}
