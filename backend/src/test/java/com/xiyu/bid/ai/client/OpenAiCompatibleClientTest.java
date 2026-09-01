package com.xiyu.bid.ai.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpenAiCompatibleClientTest {

    private OpenAiCompatibleClient createClient() {
        ObjectMapper objectMapper = new ObjectMapper();
        return new OpenAiCompatibleClient(
                new RestTemplateBuilder(),
                objectMapper,
                new AiPromptBuilder(),
                new AiResponseParser(objectMapper));
    }

    private AiProviderRuntimeConfig testConfig() {
        return new AiProviderRuntimeConfig(
                "deepseek",
                "https://api.deepseek.com/chat/completions",
                "deepseek-chat",
                "sk-test"
        );
    }

    // =========================================================================
    // 1. Existing Error Translation Tests (Backward Compatibility Preserved)
    // =========================================================================

    @Test
    void testConnection_WhenDeepSeekReturnsInsufficientBalance_ShouldExposeActionableMessage() {
        OpenAiCompatibleClient client = createClient();
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(client, "restTemplate");
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        String responseBody = """
                {"error":{"message":"Insufficient Balance","type":"unknown_error","param":null,"code":"invalid_request_error"}}
                """;

        server.expect(requestTo("https://api.deepseek.com/chat/completions"))
                .andExpect(method(POST))
                .andRespond(withStatus(HttpStatus.PAYMENT_REQUIRED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(responseBody));

        assertThatThrownBy(() -> client.testConnection(testConfig()))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("DeepSeek API 余额不足，请在 DeepSeek 控制台充值，或更换有余额的 API Key 后再测试。");

        server.verify();
    }

    @Test
    void testConnection_WhenContentEmpty_ShouldThrowWithThinkingBudgetHint() {
        // thinking 系模型（lessons #201）：max_tokens 被内部推理耗尽，正文 content 为空串
        OpenAiCompatibleClient client = createClient();
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(client, "restTemplate");
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();

        server.expect(requestTo("https://api.deepseek.com/chat/completions"))
                .andExpect(method(POST))
                .andExpect(jsonPath("$.max_tokens").value(512))
                .andRespond(withSuccess()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"choices\":[{\"message\":{\"content\":\"\"}}]}"));

        assertThatThrownBy(() -> client.testConnection(testConfig()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("thinking");

        server.verify();
    }

    @Test
    void testConnection_WhenArkReturns401WithModelNotFound_ShouldExposeProviderMessage() {
        OpenAiCompatibleClient client = createClient();
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(client, "restTemplate");
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        String responseBody = """
                {"error":{"message":"The model deepseek-v3-2 does not exist or you do not have access to it.","code":"invalid_request_error"}}
                """;

        server.expect(requestTo("https://ark.cn-beijing.volces.com/api/v3/chat/completions"))
                .andExpect(method(POST))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(responseBody));

        AiProviderRuntimeConfig config = new AiProviderRuntimeConfig(
                "doubao",
                "https://ark.cn-beijing.volces.com/api/v3/chat/completions",
                "deepseek-v3-2",
                "ark-test"
        );

        assertThatThrownBy(() -> client.testConnection(config))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("豆包 API Key 无效或无权限")
                .hasMessageContaining("The model deepseek-v3-2 does not exist");

        server.verify();
    }

    // =========================================================================
    // 2. Milestone 2: chatCompletionJsonObject Tests
    // =========================================================================

    @Test
    @DisplayName("chatCompletionJsonObject: 网关支持时发送 response_format: {\"type\": \"json_object\"} 并返回内容")
    void chatCompletionJsonObject_WhenSupported_SendsResponseFormatJsonObject() {
        OpenAiCompatibleClient client = createClient();
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(client, "restTemplate");
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();

        String successfulResponse = """
                {
                  "id": "chatcmpl-001",
                  "object": "chat.completion",
                  "choices": [
                    {
                      "index": 0,
                      "message": {
                        "role": "assistant",
                        "content": "{\\"status\\":\\"PASS\\",\\"conclusion\\":\\"资质核查完全合规\\"}"
                      }
                    }
                  ]
                }
                """;

        server.expect(requestTo("https://api.deepseek.com/chat/completions"))
                .andExpect(method(POST))
                .andExpect(jsonPath("$.model").value("deepseek-chat"))
                .andExpect(jsonPath("$.max_tokens").value(1024))
                .andExpect(jsonPath("$.response_format.type").value("json_object"))
                .andRespond(withSuccess(successfulResponse, MediaType.APPLICATION_JSON));

        String result = client.chatCompletionJsonObject(testConfig(), "请对标书进行语义裁决", 1024);

        assertThat(result).isEqualTo("{\"status\":\"PASS\",\"conclusion\":\"资质核查完全合规\"}");
        server.verify();
    }

    @Test
    @DisplayName("chatCompletionJsonObject: 网关返回 400 (不支持 response_format) 时，设置降级标志并重试裸请求成功")
    void chatCompletionJsonObject_WhenServerReturns400Unsupported_FallsBackToBareAndSetsFlag() {
        OpenAiCompatibleClient client = createClient();
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(client, "restTemplate");
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();

        String error400Body = """
                {
                  "error": {
                    "message": "response_format 'json_object' is not supported by this model",
                    "type": "invalid_request_error",
                    "code": "unsupported_parameter"
                  }
                }
                """;

        String fallback200Body = """
                {
                  "id": "chatcmpl-002",
                  "object": "chat.completion",
                  "choices": [
                    {
                      "index": 0,
                      "message": {
                        "role": "assistant",
                        "content": "{\\"status\\":\\"PASS\\",\\"conclusion\\":\\"裸请求降级成功\\"}"
                      }
                    }
                  ]
                }
                """;

        // 第 1 次调用期望：带 response_format -> 返回 400 Bad Request
        server.expect(requestTo("https://api.deepseek.com/chat/completions"))
                .andExpect(method(POST))
                .andExpect(jsonPath("$.response_format.type").value("json_object"))
                .andRespond(withBadRequest().contentType(MediaType.APPLICATION_JSON).body(error400Body));

        // 第 2 次调用期望：降级裸请求，无 response_format -> 返回 200 OK
        server.expect(requestTo("https://api.deepseek.com/chat/completions"))
                .andExpect(method(POST))
                .andExpect(jsonPath("$.response_format").doesNotExist())
                .andRespond(withSuccess(fallback200Body, MediaType.APPLICATION_JSON));

        String result = client.chatCompletionJsonObject(testConfig(), "请对标书进行语义裁决", 1024);

        assertThat(result).isEqualTo("{\"status\":\"PASS\",\"conclusion\":\"裸请求降级成功\"}");
        server.verify();

        // 验证降级标志已被置为 true
        AtomicBoolean flag = (AtomicBoolean) ReflectionTestUtils.getField(client, "responseFormatUnsupported");
        if (flag != null) {
            assertThat(flag.get()).isTrue();
        }
    }

    @Test
    @DisplayName("chatCompletionJsonObject: 降级标志置位后，后续调用直接走裸请求（单次 HTTP，不触发 400）")
    void chatCompletionJsonObject_SubsequentCalls_DirectlyUseBareCompletionWithoutHitting400() {
        OpenAiCompatibleClient client = createClient();
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(client, "restTemplate");
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();

        String error400Body = """
                {"error":{"message":"response_format unsupported","code":"unsupported_parameter"}}
                """;
        String firstSuccessBody = """
                {"choices":[{"message":{"content":"{\\"status\\":\\"PASS\\",\\"conclusion\\":\\"第一次降级成功\\"}"}}]}
                """;
        String secondSuccessBody = """
                {"choices":[{"message":{"content":"{\\"status\\":\\"FAIL\\",\\"conclusion\\":\\"第二次直接裸请求成功\\"}"}}]}
                """;

        // 第一次调用：1次结构化(400) + 1次裸请求(200)
        server.expect(requestTo("https://api.deepseek.com/chat/completions"))
                .andExpect(method(POST))
                .andExpect(jsonPath("$.response_format.type").value("json_object"))
                .andRespond(withBadRequest().contentType(MediaType.APPLICATION_JSON).body(error400Body));

        server.expect(requestTo("https://api.deepseek.com/chat/completions"))
                .andExpect(method(POST))
                .andExpect(jsonPath("$.response_format").doesNotExist())
                .andRespond(withSuccess(firstSuccessBody, MediaType.APPLICATION_JSON));

        // 第二次调用：直接 1 次裸请求（缓存生效，不先发结构化请求）
        server.expect(requestTo("https://api.deepseek.com/chat/completions"))
                .andExpect(method(POST))
                .andExpect(jsonPath("$.response_format").doesNotExist())
                .andRespond(withSuccess(secondSuccessBody, MediaType.APPLICATION_JSON));

        String firstResult = client.chatCompletionJsonObject(testConfig(), "首次调用", 1024);
        assertThat(firstResult).contains("第一次降级成功");

        String secondResult = client.chatCompletionJsonObject(testConfig(), "后续调用", 1024);
        assertThat(secondResult).contains("第二次直接裸请求成功");

        server.verify();
    }

    @Test
    @DisplayName("chatCompletionJsonObject: 非 response_format 相关的 400 错误直接抛出，不误触降级标志")
    void chatCompletionJsonObject_When400IsUnrelatedError_ThrowsWithoutSettingFlag() {
        OpenAiCompatibleClient client = createClient();
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(client, "restTemplate");
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();

        String promptTooLongError = """
                {"error":{"message":"Maximum context length exceeded","type":"invalid_request_error","code":"context_length_exceeded"}}
                """;

        server.expect(requestTo("https://api.deepseek.com/chat/completions"))
                .andExpect(method(POST))
                .andExpect(jsonPath("$.response_format.type").value("json_object"))
                .andRespond(withBadRequest().contentType(MediaType.APPLICATION_JSON).body(promptTooLongError));

        assertThatThrownBy(() -> client.chatCompletionJsonObject(testConfig(), "超长 prompt", 1024))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Maximum context length exceeded");

        server.verify();

        AtomicBoolean flag = (AtomicBoolean) ReflectionTestUtils.getField(client, "responseFormatUnsupported");
        if (flag != null) {
            assertThat(flag.get()).isFalse();
        }
    }
}
