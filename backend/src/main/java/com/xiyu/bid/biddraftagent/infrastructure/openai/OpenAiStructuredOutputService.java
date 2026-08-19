package com.xiyu.bid.biddraftagent.infrastructure.openai;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class OpenAiStructuredOutputService {

    private final OpenAiStructuredOutputTransport transport;

    OpenAiStructuredOutputService(OpenAiStructuredOutputTransport pTransport) {
        this.transport = pTransport;
    }

    public <T> T request(
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

    /**
     * 多模态过载：images 为空时委托纯文本路径（零回归）；
     * images 非空且 apiStyle 为 RESPONSES 时抛 IllegalStateException，由调用层降级纯文本。
     */
    public <T> T request(
            String prompt,
            List<MultimodalImage> images,
            Class<T> responseType,
            OpenAiBidAgentRequestConfig config,
            String missingOutputMessage
    ) {
        if (images == null || images.isEmpty()) {
            return request(prompt, responseType, config, missingOutputMessage);
        }
        if (config.apiStyle() == OpenAiBidAgentApiStyle.RESPONSES) {
            throw new IllegalStateException(
                    "multimodal image content requires CHAT_COMPLETIONS api style but got RESPONSES; "
                            + "degrade to text-only request");
        }
        return transport.requestWithChatCompletionsMultimodal(prompt, images, responseType, config)
                .orElseThrow(() -> new IllegalStateException(missingOutputMessage));
    }
}
