// Input: Adversarial payloads, simulated 400 error bodies, malformed JSON responses, AI exceptions
// Output: Empirical verification of 400 fallback chain, AtomicBoolean flag caching, and buildLlmPort retry invariants
// Pos: compliance/application/ - Milestone 2 专项对抗性验证测试
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.compliance.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.ai.client.AiPromptBuilder;
import com.xiyu.bid.ai.client.AiProvider;
import com.xiyu.bid.ai.client.AiProviderRuntimeConfig;
import com.xiyu.bid.ai.client.AiResponseParser;
import com.xiyu.bid.ai.client.OpenAiCompatibleClient;
import com.xiyu.bid.compliance.domain.policy.LlmVerdict;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@ExtendWith(MockitoExtension.class)
class Milestone2AdversarialChallengeTest {

    private static final String STRICT_JSON_RETRY_SUFFIX =
            "\n\n重申：仅输出一个 JSON 对象，不得包含任何其他文字、解释或代码块标记。";

    private AiProviderRuntimeConfig testConfig() {
        return new AiProviderRuntimeConfig(
                "openai-compatible",
                "https://ai.example.com/v1/chat/completions",
                "custom-model",
                "sk-adversarial-test"
        );
    }

    private OpenAiCompatibleClient createClient() {
        ObjectMapper objectMapper = new ObjectMapper();
        return new OpenAiCompatibleClient(
                new RestTemplateBuilder(),
                objectMapper,
                new AiPromptBuilder(),
                new AiResponseParser(objectMapper));
    }

    @Nested
    @DisplayName("1. Fallback Chain & Gateway 400 Error Stress Verification")
    class FallbackChainStressTests {

        @Test
        @DisplayName("精确验证要求1：网关返回 400 {\"error\": {\"message\": \"response_format is not supported\"}} 时自动置位并裸请求降级成功")
        void testExact400Message_ResponseFormatNotSupported_FallsBackAndSetsFlag() {
            OpenAiCompatibleClient client = createClient();
            RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(client, "restTemplate");
            MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();

            String exact400Error = "{\"error\": {\"message\": \"response_format is not supported\"}}";
            String successfulFallback = "{\"choices\":[{\"message\":{\"content\":\"{\\\"status\\\":\\\"PASS\\\",\\\"conclusion\\\":\\\"降级成功\\\"}\"}}]}";

            // 1st request: includes response_format -> server returns 400 Bad Request
            server.expect(requestTo("https://ai.example.com/v1/chat/completions"))
                    .andExpect(method(POST))
                    .andExpect(jsonPath("$.response_format.type").value("json_object"))
                    .andRespond(withBadRequest().contentType(MediaType.APPLICATION_JSON).body(exact400Error));

            // 2nd request: bare request without response_format -> server returns 200 OK
            server.expect(requestTo("https://ai.example.com/v1/chat/completions"))
                    .andExpect(method(POST))
                    .andExpect(jsonPath("$.response_format").doesNotExist())
                    .andRespond(withSuccess(successfulFallback, MediaType.APPLICATION_JSON));

            String result = client.chatCompletionJsonObject(testConfig(), "测试 Prompt 1", 1024);

            assertThat(result).isEqualTo("{\"status\":\"PASS\",\"conclusion\":\"降级成功\"}");
            server.verify();

            // Verify flag is set to true
            AtomicBoolean flag = (AtomicBoolean) ReflectionTestUtils.getField(client, "responseFormatUnsupported");
            assertThat(flag).isNotNull();
            assertThat(flag.get()).isTrue();

            // Verify subsequent call directly executes bare completion without response_format and without 400
            server.reset();
            String secondCallSuccess = "{\"choices\":[{\"message\":{\"content\":\"{\\\"status\\\":\\\"PASS\\\",\\\"conclusion\\\":\\\"第二次调用直接裸请求\\\"}\"}}]}";
            server.expect(requestTo("https://ai.example.com/v1/chat/completions"))
                    .andExpect(method(POST))
                    .andExpect(jsonPath("$.response_format").doesNotExist())
                    .andRespond(withSuccess(secondCallSuccess, MediaType.APPLICATION_JSON));

            String secondResult = client.chatCompletionJsonObject(testConfig(), "测试 Prompt 2", 1024);
            assertThat(secondResult).isEqualTo("{\"status\":\"PASS\",\"conclusion\":\"第二次调用直接裸请求\"}");
            server.verify();
        }

        @Test
        @DisplayName("对抗变体：各种网关 400 提示词（unrecognized field / extra_forbidden / json_object）均能正确触发降级")
        void testVariousGateway400Messages_AllTriggerFallback() {
            String[] errorBodies = {
                "{\"error\":{\"message\":\"Unrecognized request argument supplied: response_format\"}}",
                "{\"error\":{\"message\":\"Extra inputs are not permitted: response_format\",\"code\":\"extra_forbidden\"}}",
                "{\"error\":{\"message\":\"Invalid parameter: json_object unsupported for this model\"}}",
                "{\"error\":{\"message\":\"unknown parameter response_format\"}}",
                "{\"error\":{\"message\":\"unrecognized field: response_format\"}}"
            };

            for (String errorBody : errorBodies) {
                OpenAiCompatibleClient client = createClient();
                RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(client, "restTemplate");
                MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();

                server.expect(requestTo("https://ai.example.com/v1/chat/completions"))
                        .andExpect(method(POST))
                        .andExpect(jsonPath("$.response_format.type").value("json_object"))
                        .andRespond(withBadRequest().contentType(MediaType.APPLICATION_JSON).body(errorBody));

                server.expect(requestTo("https://ai.example.com/v1/chat/completions"))
                        .andExpect(method(POST))
                        .andExpect(jsonPath("$.response_format").doesNotExist())
                        .andRespond(withSuccess("{\"choices\":[{\"message\":{\"content\":\"OK\"}}]}", MediaType.APPLICATION_JSON));

                String result = client.chatCompletionJsonObject(testConfig(), "prompt", 512);
                assertThat(result).isEqualTo("OK");
                server.verify();
                AtomicBoolean flag = (AtomicBoolean) ReflectionTestUtils.getField(client, "responseFormatUnsupported");
                assertThat(flag).isNotNull();
                assertThat(flag.get()).isTrue();
            }
        }

        @Test
        @DisplayName("对抗验证：非 response_format 相关的 400 错误（如模型不存在/参数超限）坚决不降级且抛出异常")
        void testUnrelated400Errors_DoNotTriggerFallback() {
            OpenAiCompatibleClient client = createClient();
            RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(client, "restTemplate");
            MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();

            String tokenLimitError = "{\"error\":{\"message\":\"max_tokens must be an integer <= 4096\",\"code\":\"invalid_max_tokens\"}}";

            server.expect(requestTo("https://ai.example.com/v1/chat/completions"))
                    .andExpect(method(POST))
                    .andExpect(jsonPath("$.response_format.type").value("json_object"))
                    .andRespond(withBadRequest().contentType(MediaType.APPLICATION_JSON).body(tokenLimitError));

            assertThatThrownBy(() -> client.chatCompletionJsonObject(testConfig(), "prompt", 8192))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("max_tokens must be an integer <= 4096");

            server.verify();
            AtomicBoolean flag = (AtomicBoolean) ReflectionTestUtils.getField(client, "responseFormatUnsupported");
            assertThat(flag).isNotNull();
            assertThat(flag.get()).isFalse();
        }
    }

    @Nested
    @DisplayName("2. BidQualityCheckAsyncExecutor.buildLlmPort Retry Invariants Verification")
    class ExecutorRetryContractStressTests {

        @Mock
        private AiProvider mockAiProvider;

        private BidQualityCheckAsyncExecutor executor;

        @BeforeEach
        void setUp() {
            executor = new BidQualityCheckAsyncExecutor(
                    null, null, null, null, null,
                    mockAiProvider, null
            );
        }

        @Test
        @DisplayName("精确验证要求2-1：首次返回非法 JSON -> 附带 STRICT_JSON_RETRY_SUFFIX 重试 -> 第二次返回合法 JSON -> 返回 Success")
        void testFirstCallMalformed_SecondCallValid_ReturnsSuccessWithStrictSuffix() {
            when(mockAiProvider.chatCompletionJsonObject(anyString(), anyInt()))
                    .thenReturn("不是合法 JSON 文本")
                    .thenReturn("{\"status\":\"PASS\",\"conclusion\":\"重试后成功\"}");

            LlmVerdict.ChatOutcome<LlmVerdict> outcome = executor.buildLlmPort().chat("原始裁决 prompt");

            assertThat(outcome).isInstanceOf(LlmVerdict.Success.class);
            LlmVerdict verdict = ((LlmVerdict.Success<LlmVerdict>) outcome).verdict();
            assertThat(verdict.status()).isEqualTo("PASS");
            assertThat(verdict.conclusion()).isEqualTo("重试后成功");

            ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
            verify(mockAiProvider, times(2)).chatCompletionJsonObject(promptCaptor.capture(), eq(1024));

            assertThat(promptCaptor.getAllValues().get(0)).isEqualTo("原始裁决 prompt");
            assertThat(promptCaptor.getAllValues().get(1)).isEqualTo("原始裁决 prompt" + STRICT_JSON_RETRY_SUFFIX);
            verify(mockAiProvider, never()).chatCompletion(anyString(), anyInt());
        }

        @Test
        @DisplayName("精确验证要求2-2：第二次重试仍返回非法 JSON -> 返回 Invalid 且不再进行第 3 次重试")
        void testFirstCallMalformed_SecondCallStillMalformed_ReturnsInvalidWithout3rdRetry() {
            when(mockAiProvider.chatCompletionJsonObject(anyString(), anyInt()))
                    .thenReturn("首次非法")
                    .thenReturn("二次仍非法");

            LlmVerdict.ChatOutcome<LlmVerdict> outcome = executor.buildLlmPort().chat("原始裁决 prompt");

            assertThat(outcome).isInstanceOf(LlmVerdict.Invalid.class);
            verify(mockAiProvider, times(2)).chatCompletionJsonObject(anyString(), anyInt());
            verify(mockAiProvider, never()).chatCompletion(anyString(), anyInt());
        }

        @Test
        @DisplayName("精确验证要求2-3：AI 抛出运行时异常（如网关 504）-> 返回 Failure 且 0 次重试")
        void testAiThrowsRuntimeException_ReturnsFailureWithZeroRetries() {
            when(mockAiProvider.chatCompletionJsonObject(anyString(), anyInt()))
                    .thenThrow(new RuntimeException("Upstream 504 Gateway Timeout"));

            LlmVerdict.ChatOutcome<LlmVerdict> outcome = executor.buildLlmPort().chat("原始裁决 prompt");

            assertThat(outcome).isInstanceOf(LlmVerdict.Failure.class);
            assertThat(((LlmVerdict.Failure<LlmVerdict>) outcome).message()).isEqualTo("Upstream 504 Gateway Timeout");

            verify(mockAiProvider, times(1)).chatCompletionJsonObject(anyString(), anyInt());
            verify(mockAiProvider, never()).chatCompletion(anyString(), anyInt());
        }

        @Test
        @DisplayName("精确验证要求2-4：AI 未启用/未配置（IllegalStateException）-> 返回 Noop 且 0 次重试")
        void testAiDisabled_ReturnsNoopWithZeroRetries() {
            when(mockAiProvider.chatCompletionJsonObject(anyString(), anyInt()))
                    .thenThrow(new IllegalStateException("AI provider is not enabled"));

            LlmVerdict.ChatOutcome<LlmVerdict> outcome = executor.buildLlmPort().chat("原始裁决 prompt");

            assertThat(outcome).isInstanceOf(LlmVerdict.Noop.class);

            verify(mockAiProvider, times(1)).chatCompletionJsonObject(anyString(), anyInt());
            verify(mockAiProvider, never()).chatCompletion(anyString(), anyInt());
        }

        @Test
        @DisplayName("容错扩展验证：首次响应带 Markdown 代码围栏或中文状态 -> 首发即解析成功，0 次重试")
        void testFencedOrChineseResponse_SucceedsOnFirstAttemptWithoutRetry() {
            when(mockAiProvider.chatCompletionJsonObject(anyString(), anyInt()))
                    .thenReturn("```json\n{\"status\":\"通过\",\"conclusion\":\"完全符合要求\"}\n```");

            LlmVerdict.ChatOutcome<LlmVerdict> outcome = executor.buildLlmPort().chat("prompt");

            assertThat(outcome).isInstanceOf(LlmVerdict.Success.class);
            LlmVerdict verdict = ((LlmVerdict.Success<LlmVerdict>) outcome).verdict();
            assertThat(verdict.status()).isEqualTo("PASS");
            assertThat(verdict.conclusion()).isEqualTo("完全符合要求");

            verify(mockAiProvider, times(1)).chatCompletionJsonObject(anyString(), anyInt());
        }
    }
}
