package com.xiyu.bid.ai.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.exception.ExternalServiceException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpenAiCompatibleClientEmbeddingTest {

    private static final String EMBEDDING_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1/embeddings";
    private static final String MODEL = "text-embedding-v3";
    private static final String API_KEY = "sk-test";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void embed_WhenQwenReturnsValidResponse_ShouldReturnFloatVector() {
        OpenAiCompatibleEmbeddingClient client = createClient();
        RestTemplate restTemplate = extractRestTemplate(client);
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        String requestBody = "{\"model\":\"text-embedding-v3\",\"input\":\"hello world\"}";
        String responseBody = """
                {
                    "object": "list",
                    "data": [
                        {
                            "object": "embedding",
                            "index": 0,
                            "embedding": [0.1, 0.2, 0.3, 0.4]
                        }
                    ],
                    "model": "text-embedding-v3",
                    "usage": {"prompt_tokens": 5, "total_tokens": 5}
                }
                """;

        server.expect(requestTo(EMBEDDING_URL))
                .andExpect(method(POST))
                .andExpect(header("Authorization", "Bearer " + API_KEY))
                .andExpect(content().json(requestBody))
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        AiProviderRuntimeConfig config = new AiProviderRuntimeConfig(
                "qwen", "https://ignore/chat", "qwen-plus", API_KEY, EMBEDDING_URL, MODEL);

        float[] result = client.embed(config, "hello world");

        assertThat(result).containsExactly(0.1f, 0.2f, 0.3f, 0.4f);
        server.verify();
    }

    @Test
    void embed_WhenApiKeyMissing_ShouldRejectBeforeCallingProvider() {
        OpenAiCompatibleEmbeddingClient client = createClient();

        AiProviderRuntimeConfig config = new AiProviderRuntimeConfig(
                "qwen", "https://ignore/chat", "qwen-plus", "", EMBEDDING_URL, MODEL);

        assertThatThrownBy(() -> client.embed(config, "hello world"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("AI API key is not configured");
    }

    @Test
    void embed_WhenEmbeddingBaseUrlMissing_ShouldDeriveFromChatBaseUrl() {
        OpenAiCompatibleEmbeddingClient client = createClient();
        RestTemplate restTemplate = extractRestTemplate(client);
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        String responseBody = """
                {
                    "object": "list",
                    "data": [
                        {
                            "object": "embedding",
                            "index": 0,
                            "embedding": [0.1, 0.2, 0.3, 0.4]
                        }
                    ],
                    "model": "text-embedding-v3",
                    "usage": {"prompt_tokens": 5, "total_tokens": 5}
                }
                """;

        server.expect(requestTo("https://ignore/embeddings"))
                .andExpect(method(POST))
                .andExpect(header("Authorization", "Bearer " + API_KEY))
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        AiProviderRuntimeConfig config = new AiProviderRuntimeConfig(
                "qwen", "https://ignore/chat/completions", "qwen-plus", API_KEY, "", MODEL);

        float[] result = client.embed(config, "hello world");

        assertThat(result).containsExactly(0.1f, 0.2f, 0.3f, 0.4f);
        server.verify();
    }

    @Test
    void embed_WhenEmbeddingBaseUrlAndChatBaseUrlMissing_ShouldRejectBeforeCallingProvider() {
        OpenAiCompatibleEmbeddingClient client = createClient();

        AiProviderRuntimeConfig config = new AiProviderRuntimeConfig(
                "qwen", "", "qwen-plus", API_KEY, "", MODEL);

        assertThatThrownBy(() -> client.embed(config, "hello world"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AI embedding base URL 无法推导");
    }

    @Test
    void embed_WhenEmbeddingModelMissing_ShouldRejectBeforeCallingProvider() {
        OpenAiCompatibleEmbeddingClient client = createClient();

        AiProviderRuntimeConfig config = new AiProviderRuntimeConfig(
                "qwen", "https://ignore/chat", "qwen-plus", API_KEY, EMBEDDING_URL, "");

        assertThatThrownBy(() -> client.embed(config, "hello world"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AI embedding model 未配置");
    }

    @Test
    void embed_WhenProviderReturns429_ShouldRetryAndThenThrowExternalServiceException() {
        OpenAiCompatibleEmbeddingClient client = createClient();
        RestTemplate restTemplate = extractRestTemplate(client);
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        String responseBody = "{\"error\":{\"message\":\"rate limit\",\"code\":\"rate_limit\"}}";

        server.expect(requestTo(EMBEDDING_URL))
                .andExpect(method(POST))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(responseBody));
        server.expect(requestTo(EMBEDDING_URL))
                .andExpect(method(POST))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(responseBody));
        server.expect(requestTo(EMBEDDING_URL))
                .andExpect(method(POST))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(responseBody));

        AiProviderRuntimeConfig config = new AiProviderRuntimeConfig(
                "qwen", "https://ignore/chat", "qwen-plus", API_KEY, EMBEDDING_URL, MODEL);

        assertThatThrownBy(() -> client.embed(config, "hello world"))
                .isInstanceOf(ExternalServiceException.class)
                .satisfies(ex -> assertThat(((ExternalServiceException) ex).getUpstreamStatusCode()).isEqualTo(429));

        server.verify();
    }

    @Test
    void embed_WhenProviderReturnsMalformedEmbeddingList_ShouldThrowRuntimeException() {
        OpenAiCompatibleEmbeddingClient client = createClient();
        RestTemplate restTemplate = extractRestTemplate(client);
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        String responseBody = "{\"data\": []}";

        server.expect(requestTo(EMBEDDING_URL))
                .andExpect(method(POST))
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        AiProviderRuntimeConfig config = new AiProviderRuntimeConfig(
                "qwen", "https://ignore/chat", "qwen-plus", API_KEY, EMBEDDING_URL, MODEL);

        assertThatThrownBy(() -> client.embed(config, "hello world"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("embedding");

        server.verify();
    }

    private OpenAiCompatibleEmbeddingClient createClient() {
        return new OpenAiCompatibleEmbeddingClient(new RestTemplateBuilder(), objectMapper);
    }

    private RestTemplate extractRestTemplate(OpenAiCompatibleEmbeddingClient client) {
        return (RestTemplate) ReflectionTestUtils.getField(client, "restTemplate");
    }
}
