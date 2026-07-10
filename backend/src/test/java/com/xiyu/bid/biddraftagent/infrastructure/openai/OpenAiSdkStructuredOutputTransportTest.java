package com.xiyu.bid.biddraftagent.infrastructure.openai;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OpenAiSdkStructuredOutputTransport 根因行为测试.
 *
 * <p>根因：PR !1979 修复了 fallback 触发条件（json_schema 不支持时 fallback 到 json_object），
 * 但每次请求都先尝试注定失败的 json_schema（等 10-25 秒返回 BadRequest），
   再 fallback 到 json_object（再 10-25 秒），导致双倍调用、前端超时。</p>
 *
 * <p>PR !1982 的修复：用 {@link AtomicBoolean} 缓存 json_schema 不支持状态，
 * 第一次失败后后续请求直接走 json_object，避免双倍调用。</p>
 *
 * <p>测试验证两个核心行为：
 * <ol>
 *   <li>首次 json_schema 失败后，{@code jsonSchemaUnsupported} 标志被设置为 true</li>
 *   <li>标志为 true 时，后续请求直接走 json_object（只有 1 次 HTTP 调用）</li>
 * </ol>
 * </p>
 */
class OpenAiSdkStructuredOutputTransportTest {

    private MockWebServer server;
    private OpenAiSdkStructuredOutputTransport transport;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        ObjectMapper objectMapper = new ObjectMapper();
        OpenAiJsonObjectPayloadReader payloadReader = new OpenAiJsonObjectPayloadReader(objectMapper);
        transport = new OpenAiSdkStructuredOutputTransport(objectMapper, payloadReader);
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    /**
     * 根因行为测试 1：缓存标志为 true 时，直接走 json_object 路径.
     *
     * <p>验证：当 {@code jsonSchemaUnsupported = true} 时，调用
     * {@code requestWithChatCompletions} 只发送 1 个 HTTP 请求（json_object），
     * 而不是先尝试 json_schema 再 fallback。</p>
     */
    @Test
    void requestWithChatCompletions_skipsJsonSchemaWhenCacheFlagSet() throws Exception {
        setJsonSchemaUnsupported(true);

        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(chatCompletionResponse("{\"value\":\"test\"}")));

        Optional<TestPayload> result = transport.requestWithChatCompletions(
                "test prompt",
                TestPayload.class,
                config(server.url("/v1").toString())
        );

        assertThat(result).isPresent();
        assertThat(result.get().value).isEqualTo("test");
        assertThat(server.getRequestCount())
                .as("缓存命中后应只发 1 个请求（json_object），不先尝试 json_schema")
                .isEqualTo(1);

        RecordedRequest request = server.takeRequest();
        assertThat(request.getPath()).isEqualTo("/v1/chat/completions");
    }

    /**
     * 根因行为测试 2：首次 json_schema 失败后缓存标志被设置，后续请求直接走 json_object.
     *
     * <p>验证完整流程：
     * <ol>
     *   <li>第一次调用：json_schema 返回 400（含 "response_format"）→ fallback 到 json_object
     *       → 共 2 个 HTTP 请求 → {@code jsonSchemaUnsupported} 被设置为 true</li>
     *   <li>第二次调用：直接走 json_object → 只有 1 个 HTTP 请求</li>
     * </ol>
     * </p>
     */
    @Test
    void requestWithChatCompletions_cachesAfterFirstFailure() throws Exception {
        // 第一次调用：json_schema 失败（400）+ json_object 成功（200）
        server.enqueue(new MockResponse()
                .setResponseCode(400)
                .setHeader("Content-Type", "application/json")
                .setBody(errorResponse("response_format unavailable on this model")));
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(chatCompletionResponse("{\"value\":\"first\"}")));

        Optional<TestPayload> result1 = transport.requestWithChatCompletions(
                "first prompt",
                TestPayload.class,
                config(server.url("/v1").toString())
        );

        assertThat(result1).isPresent();
        assertThat(result1.get().value).isEqualTo("first");
        assertThat(server.getRequestCount())
                .as("首次调用应发 2 个请求（json_schema 失败 + json_object fallback）")
                .isEqualTo(2);
        assertThat(getJsonSchemaUnsupported())
                .as("首次 json_schema 失败后缓存标志应被设置为 true")
                .isTrue();

        // 第二次调用：缓存命中，直接走 json_object
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(chatCompletionResponse("{\"value\":\"second\"}")));

        Optional<TestPayload> result2 = transport.requestWithChatCompletions(
                "second prompt",
                TestPayload.class,
                config(server.url("/v1").toString())
        );

        assertThat(result2).isPresent();
        assertThat(result2.get().value).isEqualTo("second");
        assertThat(server.getRequestCount())
                .as("第二次调用应只发 1 个请求（缓存命中，直接 json_object）")
                .isEqualTo(3);
    }

    // --- helpers ---

    private void setJsonSchemaUnsupported(boolean value) throws Exception {
        Field field = OpenAiSdkStructuredOutputTransport.class
                .getDeclaredField("jsonSchemaUnsupported");
        field.setAccessible(true);
        AtomicBoolean flag = (AtomicBoolean) field.get(transport);
        flag.set(value);
    }

    private boolean getJsonSchemaUnsupported() throws Exception {
        Field field = OpenAiSdkStructuredOutputTransport.class
                .getDeclaredField("jsonSchemaUnsupported");
        field.setAccessible(true);
        AtomicBoolean flag = (AtomicBoolean) field.get(transport);
        return flag.get();
    }

    private OpenAiBidAgentRequestConfig config(String baseUrl) {
        return new OpenAiBidAgentRequestConfig(
                "sk-test",
                baseUrl,
                "test-model",
                Duration.ofSeconds(30),
                OpenAiBidAgentApiStyle.CHAT_COMPLETIONS
        );
    }

    private String chatCompletionResponse(String contentJson) {
        String escaped = escapeJsonContent(contentJson);
        return """
                {
                  "id": "chatcmpl-test",
                  "object": "chat.completion",
                  "created": 1700000000,
                  "model": "test-model",
                  "choices": [
                    {
                      "index": 0,
                      "message": {
                        "role": "assistant",
                        "content": %s
                      },
                      "finish_reason": "stop",
                      "logprobs": null
                    }
                  ],
                  "usage": {
                    "prompt_tokens": 10,
                    "completion_tokens": 20,
                    "total_tokens": 30
                  }
                }
                """.formatted(escaped);
    }

    private String errorResponse(String message) {
        return """
                {
                  "error": {
                    "message": "%s",
                    "type": "invalid_request_error",
                    "code": "unsupported_parameter"
                  }
                }
                """.formatted(message);
    }

    private String escapeJsonContent(String json) {
        StringBuilder sb = new StringBuilder();
        sb.append('"');
        for (char c : json.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> sb.append(c);
            }
        }
        sb.append('"');
        return sb.toString();
    }

    public static class TestPayload {
        public String value;
    }
}
