package com.xiyu.bid.ai.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class QwenEmbeddingClientTest {

    private static final String DEFAULT_EMBEDDING_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1/embeddings";
    private static final String API_KEY = "sk-qwen";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void embed_WhenUsingDefaultConfig_ShouldCallQwenEmbeddingsEndpoint() {
        OpenAiCompatibleEmbeddingClient openAiClient = createOpenAiClient();
        RestTemplate restTemplate = extractRestTemplate(openAiClient);
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

        server.expect(requestTo(DEFAULT_EMBEDDING_URL))
                .andExpect(method(POST))
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        QwenEmbeddingClient client = new QwenEmbeddingClient(openAiClient);
        AiProviderRuntimeConfig config = new AiProviderRuntimeConfig(
                "qwen", "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions",
                "qwen-plus", API_KEY, null, null);

        float[] result = client.embed(config, "测试文本");

        assertThat(result).containsExactly(0.5f, -0.5f, 0.0f, 1.0f);
        server.verify();
    }

    @Test
    void embed_WhenConfigOverridesDefaults_ShouldUseConfiguredValues() {
        OpenAiCompatibleEmbeddingClient openAiClient = createOpenAiClient();
        RestTemplate restTemplate = extractRestTemplate(openAiClient);
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

        QwenEmbeddingClient client = new QwenEmbeddingClient(openAiClient);
        AiProviderRuntimeConfig config = new AiProviderRuntimeConfig(
                "qwen", "https://ignore/chat", "qwen-plus", API_KEY, customUrl, customModel);

        float[] result = client.embed(config, "custom");

        assertThat(result).containsExactly(0.9f, 0.8f, 0.7f);
        server.verify();
    }

    @Test
    void defaultEmbeddingBaseUrl_ShouldPointToQwenCompatibleEndpoint() {
        assertThat(QwenEmbeddingClient.DEFAULT_EMBEDDING_BASE_URL)
                .isEqualTo(DEFAULT_EMBEDDING_URL);
    }

    @Test
    void defaultEmbeddingModel_ShouldBeTextEmbeddingV3() {
        assertThat(QwenEmbeddingClient.DEFAULT_EMBEDDING_MODEL)
                .isEqualTo("text-embedding-v3");
    }

    @Test
    void defaultDimension_ShouldBe1024() {
        assertThat(QwenEmbeddingClient.DEFAULT_DIMENSION).isEqualTo(1024);
    }

    private OpenAiCompatibleEmbeddingClient createOpenAiClient() {
        return new OpenAiCompatibleEmbeddingClient(new RestTemplateBuilder(), objectMapper);
    }

    private RestTemplate extractRestTemplate(OpenAiCompatibleEmbeddingClient client) {
        return (RestTemplate) ReflectionTestUtils.getField(client, "restTemplate");
    }
}
