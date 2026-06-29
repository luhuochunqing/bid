package com.xiyu.bid.ai.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.http.HttpMethod.POST;

class OpenAiCompatibleClientTest {

    @Test
    void testConnection_WhenDeepSeekReturnsInsufficientBalance_ShouldExposeActionableMessage() {
        ObjectMapper objectMapper = new ObjectMapper();
        OpenAiCompatibleClient client = new OpenAiCompatibleClient(
                new RestTemplateBuilder(),
                objectMapper,
                new AiPromptBuilder(),
                new AiResponseParser(objectMapper));
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

        AiProviderRuntimeConfig config = new AiProviderRuntimeConfig(
                "deepseek",
                "https://api.deepseek.com/chat/completions",
                "deepseek-chat",
                "sk-test"
        );

        assertThatThrownBy(() -> client.testConnection(config))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("DeepSeek API 余额不足，请在 DeepSeek 控制台充值，或更换有余额的 API Key 后再测试。");

        server.verify();
    }

    @Test
    void testConnection_WhenArkReturns401WithModelNotFound_ShouldExposeProviderMessage() {
        ObjectMapper objectMapper = new ObjectMapper();
        OpenAiCompatibleClient client = new OpenAiCompatibleClient(
                new RestTemplateBuilder(),
                objectMapper,
                new AiPromptBuilder(),
                new AiResponseParser(objectMapper));
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
}
