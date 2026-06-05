package com.xiyu.bid.biddraftagent.infrastructure.openai;

import java.util.Optional;

interface OpenAiStructuredOutputTransport {

    <T> Optional<T> requestWithResponses(
            String prompt,
            Class<T> responseType,
            OpenAiBidAgentRequestConfig config
    );

    <T> Optional<T> requestWithChatCompletions(
            String prompt,
            Class<T> responseType,
            OpenAiBidAgentRequestConfig config
    );
}
