package com.xiyu.bid.biddraftagent.infrastructure.openai;

import org.springframework.stereotype.Service;

@Service
class OpenAiStructuredOutputService {

    private final OpenAiStructuredOutputTransport transport;

    OpenAiStructuredOutputService(OpenAiStructuredOutputTransport pTransport) {
        this.transport = pTransport;
    }

    <T> T request(
            String prompt,
            Class<T> responseType,
            OpenAiBidAgentRequestConfig config,
            String missingOutputMessage
    ) {
        return switch (config.apiStyle()) {
            case RESPONSES -> transport.requestWithResponses(prompt, responseType, config)
                    .orElseThrow(() -> new IllegalStateException(missingOutputMessage));
            case CHAT_COMPLETIONS -> transport.requestWithChatCompletions(prompt, responseType, config)
                    .orElseThrow(() -> new IllegalStateException(missingOutputMessage));
        };
    }
}
