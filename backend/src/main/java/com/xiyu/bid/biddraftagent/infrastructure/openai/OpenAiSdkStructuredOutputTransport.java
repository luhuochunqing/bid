package com.xiyu.bid.biddraftagent.infrastructure.openai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.errors.BadRequestException;
import com.openai.models.ResponseFormatJsonObject;
import com.openai.models.chat.completions.ChatCompletionContentPart;
import com.openai.models.chat.completions.ChatCompletionContentPartImage;
import com.openai.models.chat.completions.ChatCompletionContentPartText;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionMessage;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.StructuredChatCompletion;
import com.openai.models.chat.completions.StructuredChatCompletionCreateParams;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.StructuredResponse;
import com.openai.models.responses.StructuredResponseCreateParams;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@Slf4j
class OpenAiSdkStructuredOutputTransport implements OpenAiStructuredOutputTransport {

    private final ObjectMapper objectMapper;
    private final OpenAiJsonObjectPayloadReader jsonObjectPayloadReader;

    /**
     * 缓存：当前 AI 网关是否不支持 json_schema 结构化输出。
     * 第一次 json_schema 调用失败后置为 true，后续请求直接走 json_object，避免双倍调用。
     */
    private final AtomicBoolean jsonSchemaUnsupported = new AtomicBoolean(false);

    OpenAiSdkStructuredOutputTransport(
            ObjectMapper pObjectMapper,
            OpenAiJsonObjectPayloadReader pJsonObjectPayloadReader
    ) {
        this.objectMapper = pObjectMapper;
        this.jsonObjectPayloadReader = pJsonObjectPayloadReader;
    }

    @Override
    public <T> Optional<T> requestWithResponses(
            String prompt,
            Class<T> responseType,
            OpenAiBidAgentRequestConfig config
    ) {
        StructuredResponseCreateParams<T> params = ResponseCreateParams.builder()
                .input(prompt)
                .model(config.model())
                .text(responseType)
                .build();
        StructuredResponse<T> response = client(config).responses().create(params);
        return response.output().stream()
                .map(item -> item.message())
                .flatMap(Optional::stream)
                .flatMap(message -> message.content().stream())
                .map(content -> content.outputText())
                .flatMap(Optional::stream)
                .findFirst();
    }

    @Override
    public <T> Optional<T> requestWithChatCompletions(
            String prompt,
            Class<T> responseType,
            OpenAiBidAgentRequestConfig config
    ) {
        if (jsonSchemaUnsupported.get()) {
            return requestWithJsonObject(prompt, responseType, config);
        }
        try {
            StructuredChatCompletionCreateParams<T> params = ChatCompletionCreateParams.builder()
                    .addUserMessage(prompt)
                    .model(config.model())
                    .responseFormat(responseType)
                    .build();
            StructuredChatCompletion<T> completion = client(config).chat().completions().create(params);
            logRawChatCompletion(completion, config);
            return completion.choices().stream()
                    .findFirst()
                    .flatMap(choice -> choice.message().content());
        } catch (BadRequestException exception) {
            if (supportsJsonObjectFallback(exception)) {
                log.warn("AI 网关不支持 json_schema 结构化输出，后续请求将直接使用 json_object 模式。错误: {}",
                        preview(exception.getMessage()));
                jsonSchemaUnsupported.set(true);
                return requestWithJsonObject(prompt, responseType, config);
            }
            throw exception;
        }
    }

    @Override
    public <T> Optional<T> requestWithChatCompletionsMultimodal(
            String prompt,
            List<MultimodalImage> images,
            Class<T> responseType,
            OpenAiBidAgentRequestConfig config
    ) {
        if (jsonSchemaUnsupported.get()) {
            return requestWithJsonObjectMultimodal(prompt, images, responseType, config);
        }
        try {
            StructuredChatCompletionCreateParams<T> params = ChatCompletionCreateParams.<T>builder()
                    .addUserMessageOfArrayOfContentParts(contentParts(prompt, images))
                    .model(config.model())
                    .responseFormat(responseType)
                    .build();
            StructuredChatCompletion<T> completion = client(config).chat().completions().create(params);
            logRawChatCompletion(completion, config);
            return completion.choices().stream()
                    .findFirst()
                    .flatMap(choice -> choice.message().content());
        } catch (BadRequestException exception) {
            if (supportsJsonObjectFallback(exception)) {
                log.warn("AI 网关不支持 json_schema 结构化输出，多模态请求降级 json_object 模式。错误: {}",
                        preview(exception.getMessage()));
                jsonSchemaUnsupported.set(true);
                return requestWithJsonObjectMultimodal(prompt, images, responseType, config);
            }
            throw exception;
        }
    }

    private OpenAIClient client(OpenAiBidAgentRequestConfig config) {
        return OpenAIOkHttpClient.builder()
                .apiKey(config.apiKey())
                .baseUrl(config.baseUrl())
                .timeout(config.timeout())
                .build();
    }

    private <T> Optional<T> requestWithJsonObject(
            String prompt,
            Class<T> responseType,
            OpenAiBidAgentRequestConfig config
    ) {
        ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                .addUserMessage(jsonObjectPrompt(prompt))
                .model(config.model())
                .responseFormat(ResponseFormatJsonObject.builder().build())
                .build();
        ChatCompletion completion = client(config).chat().completions().create(params);
        logRawChatCompletion(completion, config);
        return completion.choices().stream()
                .findFirst()
                .map(ChatCompletion.Choice::message)
                .flatMap(ChatCompletionMessage::content)
                .map(content -> readValue(content, responseType));
    }

    private String jsonObjectPrompt(String prompt) {
        return """
                Return exactly one valid JSON object.
                The response must be valid json.
                Do not wrap it in markdown fences.
                Use the requested field names exactly as provided.

                %s
                """.formatted(prompt);
    }

    /**
     * 组装多模态 content parts：文本 part 在第一位，其后按顺序跟随全部图片 data URL。
     */
    private List<ChatCompletionContentPart> contentParts(String text, List<MultimodalImage> images) {
        List<ChatCompletionContentPart> parts = new ArrayList<>();
        parts.add(ChatCompletionContentPart.ofText(
                ChatCompletionContentPartText.builder().text(text).build()));
        for (MultimodalImage image : images) {
            parts.add(ChatCompletionContentPart.ofImageUrl(
                    ChatCompletionContentPartImage.builder()
                            .imageUrl(ChatCompletionContentPartImage.ImageUrl.builder()
                                    .url(image.dataUrl())
                                    .build())
                            .build()));
        }
        return List.copyOf(parts);
    }

    private <T> Optional<T> requestWithJsonObjectMultimodal(
            String prompt,
            List<MultimodalImage> images,
            Class<T> responseType,
            OpenAiBidAgentRequestConfig config
    ) {
        ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                .addUserMessageOfArrayOfContentParts(contentParts(jsonObjectPrompt(prompt), images))
                .model(config.model())
                .responseFormat(ResponseFormatJsonObject.builder().build())
                .build();
        ChatCompletion completion = client(config).chat().completions().create(params);
        logRawChatCompletion(completion, config);
        return completion.choices().stream()
                .findFirst()
                .map(ChatCompletion.Choice::message)
                .flatMap(ChatCompletionMessage::content)
                .map(content -> readValue(content, responseType));
    }

    private boolean supportsJsonObjectFallback(BadRequestException exception) {
        String message = exception.getMessage();
        if (message == null) {
            return false;
        }
        String lower = message.toLowerCase();
        // 以下情况说明上游网关不支持 json_schema 结构化输出，应 fallback 到 json_object：
        // 1. 明确说 response_format unavailable（OpenAI 官方错误消息）
        // 2. 提到 response_format 但不支持（qwen/通义千问等国内网关的错误消息）
        // 3. 直接提到 json_schema 不被支持
        return lower.contains("response_format")
                || lower.contains("json_schema");
    }

    private <T> T readValue(String content, Class<T> responseType) {
        try {
            return jsonObjectPayloadReader.read(content, responseType);
        } catch (JsonProcessingException exception) {
            log.warn("AI json_object parse failed for {}: {} preview: {}",
                    responseType.getSimpleName(),
                    exception.getOriginalMessage(),
                    preview(content));
            try {
                return objectMapper.readerFor(responseType)
                        .without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                        .with(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
                        .with(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT)
                        .readValue(content);
            } catch (JsonProcessingException fallbackException) {
                throw new IllegalStateException("AI json_object response could not be parsed", fallbackException);
            }
        }
    }

    /** 诊断用：需将 com.xiyu.bid.biddraftagent.infrastructure.openai 日志级别调到 DEBUG 才会输出。 */
    private <T> void logRawChatCompletion(StructuredChatCompletion<T> completion, OpenAiBidAgentRequestConfig config) {
        if (!log.isDebugEnabled()) {
            return;
        }
        var choice = completion.rawChatCompletion().choices().stream().findFirst().orElse(null);
        String raw = choice == null ? "<no-choice>" : choice.message().content().orElse("<empty>");
        log.debug("LLM 原始响应(json_schema): model={}, finishReason={}, usage={}, chars={}, body={}",
                config.model(), choice == null ? "<none>" : choice.finishReason(),
                completion.usage().map(Object::toString).orElse("<none>"), raw.length(), excerpt(raw, 1500));
    }

    /** 诊断用：需将 com.xiyu.bid.biddraftagent.infrastructure.openai 日志级别调到 DEBUG 才会输出。 */
    private void logRawChatCompletion(ChatCompletion completion, OpenAiBidAgentRequestConfig config) {
        if (!log.isDebugEnabled()) {
            return;
        }
        var choice = completion.choices().stream().findFirst().orElse(null);
        String raw = choice == null ? "<no-choice>" : choice.message().content().orElse("<empty>");
        log.debug("LLM 原始响应(json_object): model={}, finishReason={}, usage={}, chars={}, body={}",
                config.model(), choice == null ? "<none>" : choice.finishReason(),
                completion.usage().map(Object::toString).orElse("<none>"), raw.length(), excerpt(raw, 1500));
    }

    /** 截断预览，保留原始换行转义，避免日志被多行撑开。 */
    private String excerpt(String content, int max) {
        if (content == null || content.isBlank()) {
            return "<empty>";
        }
        String singleLine = content.replace("\r", "").replace("\n", "\\n");
        return singleLine.length() <= max ? singleLine
                : singleLine.substring(0, max) + "...[truncated, total " + singleLine.length() + " chars]";
    }

    private String preview(String content) {
        if (content == null || content.isBlank()) {
            return "<empty>";
        }
        String normalized = content.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 400 ? normalized : normalized.substring(0, 400) + "...";
    }
}
