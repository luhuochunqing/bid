package com.xiyu.bid.ai.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpenAiCompatibleEmbeddingClientTest {

    private static final String QWEN_CHAT_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";
    private static final String QWEN_EMBED_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1/embeddings";
    private static final String API_KEY = "sk-qwen";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void embed_WhenUsingDefaultConfig_ShouldCallEmbeddingsEndpoint() {
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
                            "embedding": [0.5, -0.5, 0.0, 1.0]
                        }
                    ],
                    "model": "text-embedding-v3",
                    "usage": {"prompt_tokens": 3, "total_tokens": 3}
                }
                """;

        server.expect(requestTo(QWEN_EMBED_URL))
                .andExpect(method(POST))
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        AiProviderRuntimeConfig config = new AiProviderRuntimeConfig(
                "qwen", QWEN_CHAT_URL, "qwen-plus", API_KEY, null, "text-embedding-v3");

        float[] result = client.embed(config, "测试文本");

        assertThat(result).containsExactly(0.5f, -0.5f, 0.0f, 1.0f);
        server.verify();
    }

    @Test
    void embed_WhenConfigOverridesDefaults_ShouldUseConfiguredValues() {
        OpenAiCompatibleEmbeddingClient client = createClient();
        RestTemplate restTemplate = extractRestTemplate(client);
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        String customUrl = "https://custom.example.com/v1/embeddings";
        String customModel = "text-embedding-v2";
        String responseBody = """
                {
                    "object": "list",
                    "data": [
                        {
                            "object": "embedding",
                            "index": 0,
                            "embedding": [0.9, 0.8, 0.7]
                        }
                    ],
                    "model": "text-embedding-v2",
                    "usage": {"prompt_tokens": 2, "total_tokens": 2}
                }
                """;

        server.expect(requestTo(customUrl))
                .andExpect(method(POST))
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        AiProviderRuntimeConfig config = new AiProviderRuntimeConfig(
                "qwen", "https://ignore/chat", "qwen-plus", API_KEY, customUrl, customModel);

        float[] result = client.embed(config, "custom");

        assertThat(result).containsExactly(0.9f, 0.8f, 0.7f);
        server.verify();
    }

    @Test
    void embed_WhenEmbeddingModelNotConfigured_ShouldThrowClearError() {
        OpenAiCompatibleEmbeddingClient client = createClient();

        AiProviderRuntimeConfig config = new AiProviderRuntimeConfig(
                "custom", "https://ai-tech.ehsy.com/v1/chat/completions", "qwen3.7-max", API_KEY, null, null);

        assertThatThrownBy(() -> client.embed(config, "test"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("embedding model 未配置")
                .hasMessageContaining("custom");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions | https://dashscope.aliyuncs.com/compatible-mode/v1/embeddings
            https://api.openai.com/v1/chat/completions                 | https://api.openai.com/v1/embeddings
            https://ai-tech.ehsy.com/v1/chat/completions               | https://ai-tech.ehsy.com/v1/embeddings
            https://ark.cn-beijing.volces.com/api/v3/chat/completions   | https://ark.cn-beijing.volces.com/api/v3/embeddings
            https://custom.example.com/v1                               | https://custom.example.com/v1/embeddings
            https://custom.example.com/v1/                              | https://custom.example.com/v1/embeddings
            https://custom.example.com                                  | https://custom.example.com/v1/embeddings
            https://custom.example.com/                                 | https://custom.example.com/v1/embeddings
            """)
    void deriveEmbeddingUrl_ShouldDeriveCorrectly(String chatUrl, String expectedEmbeddingUrl) {
        assertThat(OpenAiCompatibleEmbeddingClient.deriveEmbeddingUrl(chatUrl))
                .isEqualTo(expectedEmbeddingUrl);
    }

    @Test
    void deriveEmbeddingUrl_WhenNull_ShouldReturnNull() {
        assertThat(OpenAiCompatibleEmbeddingClient.deriveEmbeddingUrl(null)).isNull();
    }

    @Test
    void deriveEmbeddingUrl_WhenBlank_ShouldReturnNull() {
        assertThat(OpenAiCompatibleEmbeddingClient.deriveEmbeddingUrl("  ")).isNull();
    }

    @Test
    void defaultEmbeddingModel_ShouldBeTextEmbeddingV3() {
        assertThat(OpenAiCompatibleEmbeddingClient.DEFAULT_EMBEDDING_MODEL)
                .isEqualTo("text-embedding-v3");
    }

    private OpenAiCompatibleEmbeddingClient createClient() {
        return new OpenAiCompatibleEmbeddingClient(new RestTemplateBuilder(), objectMapper);
    }

    private RestTemplate extractRestTemplate(OpenAiCompatibleEmbeddingClient client) {
        return (RestTemplate) ReflectionTestUtils.getField(client, "restTemplate");
    }
}