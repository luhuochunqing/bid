package com.xiyu.bid.biddraftagent.infrastructure.openai;

import java.util.List;
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

    /**
     * 多模态 CHAT_COMPLETIONS 请求：一条用户消息含文本 prompt + 多张图片（data URL）。
     * 仅 CHAT_COMPLETIONS api style 支持；RESPONSES 风格由调用层降级纯文本。
     */
    <T> Optional<T> requestWithChatCompletionsMultimodal(
            String prompt,
            List<MultimodalImage> images,
            Class<T> responseType,
            OpenAiBidAgentRequestConfig config
    );
}
