package com.xiyu.bid.biddraftagent.infrastructure.openai;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ScoreExtractionTransportBudgetTest {
    @Test void delayedResponseTimeoutSendsOneRequestWithoutHiddenRetries() throws Exception {
        try (var server = new MockWebServer()) {
            server.start();
            for (int i = 0; i < 3; i++) server.enqueue(new MockResponse().setBody("{}")
                    .setHeadersDelay(400, TimeUnit.MILLISECONDS));
            var json = new ObjectMapper();
            var transport = new OpenAiSdkStructuredOutputTransport(json, new OpenAiJsonObjectPayloadReader(json));
            var config = new OpenAiBidAgentRequestConfig("diagnostic-only", server.url("/v1").toString(),
                    "test-model", Duration.ofMillis(100), OpenAiBidAgentApiStyle.CHAT_COMPLETIONS, 0);
            assertThatThrownBy(() -> transport.requestWithChatCompletions("synthetic", Payload.class, config))
                    .isInstanceOf(RuntimeException.class);
            assertThat(server.getRequestCount()).isEqualTo(1);
        }
    }
    @Test void transientServerErrorDoesNotDuplicateScoringRequest() throws Exception {
        try (var server = new MockWebServer()) {
            server.start();
            for (int i = 0; i < 3; i++) server.enqueue(new MockResponse().setResponseCode(503)
                    .setBody("{\"error\":{\"message\":\"unavailable\"}}"));
            var json = new ObjectMapper();
            var transport = new OpenAiSdkStructuredOutputTransport(json, new OpenAiJsonObjectPayloadReader(json));
            var config = new OpenAiBidAgentRequestConfig("diagnostic-only", server.url("/v1").toString(),
                    "test-model", Duration.ofSeconds(3), OpenAiBidAgentApiStyle.CHAT_COMPLETIONS, 0);
            assertThatThrownBy(() -> transport.requestWithChatCompletions("synthetic", Payload.class, config))
                    .isInstanceOf(RuntimeException.class);
            assertThat(server.getRequestCount()).isEqualTo(1);
        }
    }
    @Test void boundedWaitReceivesDelayedValidResponseAndCarriesCorrelationId() throws Exception {
        try (var server = new MockWebServer()) {
            server.start();
            String body = "{\"id\":\"synthetic\",\"object\":\"chat.completion\",\"created\":0,"
                    + "\"model\":\"test-model\",\"choices\":[{\"index\":0,\"finish_reason\":\"stop\","
                    + "\"message\":{\"role\":\"assistant\",\"content\":\"{\\\"value\\\":\\\"ok\\\"}\"}}]}";
            server.enqueue(new MockResponse().setHeader("Content-Type", "application/json")
                    .setHeadersDelay(250, TimeUnit.MILLISECONDS).setBody(body));
            var json = new ObjectMapper();
            var transport = new OpenAiSdkStructuredOutputTransport(json, new OpenAiJsonObjectPayloadReader(json));
            var config = new OpenAiBidAgentRequestConfig("diagnostic-only", server.url("/v1").toString(),
                    "test-model", Duration.ofSeconds(3), OpenAiBidAgentApiStyle.CHAT_COMPLETIONS, 0);
            assertThat(transport.requestWithChatCompletions("synthetic", Payload.class, config))
                    .get().extracting(v -> v.value).isEqualTo("ok");
            assertThat(server.getRequestCount()).isEqualTo(1);
            assertThat(server.takeRequest().getHeader("X-Request-Id")).isNotBlank();
        }
    }
    @Test void realResolverExtractorAndSdkShareTheZeroRetryPolicy() throws Exception {
        try (var server = new MockWebServer()) {
            server.start();
            for (int i = 0; i < 3; i++) server.enqueue(new MockResponse().setResponseCode(503)
                    .setBody("{\"error\":{\"message\":\"unavailable\"}}"));
            var settings = org.mockito.Mockito.mock(com.xiyu.bid.settings.service.AiConfigService.class);
            var provider = com.xiyu.bid.settings.dto.SettingsResponse.AiProviderSetting.builder()
                    .providerCode("custom").enabled(true).model("test-model")
                    .baseUrl(server.url("/v1/chat/completions").toString()).build();
            org.mockito.Mockito.when(settings.getInternalAiModelConfig()).thenReturn(
                    com.xiyu.bid.settings.dto.SettingsResponse.AiModelConfig.builder()
                            .activeProvider("custom").providers(java.util.List.of(provider)).build());
            org.mockito.Mockito.when(settings.resolveAiApiKey("custom")).thenReturn("diagnostic-only");
            var resolver = new OpenAiBidAgentConfigurationResolver(settings,
                    new com.xiyu.bid.settings.service.AiProviderCatalog(),
                    new org.springframework.mock.env.MockEnvironment(), Duration.ofSeconds(30), Duration.ofSeconds(90));
            var json = new ObjectMapper();
            var transport = new OpenAiSdkStructuredOutputTransport(json, new OpenAiJsonObjectPayloadReader(json));
            var extractor = new com.xiyu.bid.scoreparse.infrastructure.openai.OpenAiMultimodalScoreFactExtractor(
                    new OpenAiStructuredOutputService(transport), resolver, json);
            assertThatThrownBy(() -> extractor.extractTextAndProgress("采购要求", () -> {}))
                    .isInstanceOf(com.xiyu.bid.scoreparse.application.ScoreModelRequestException.class);
            assertThat(server.getRequestCount()).isEqualTo(1);
            assertThat(resolver.resolve("multimodal score fact extraction").timeout()).isEqualTo(Duration.ofSeconds(300));
            assertThat(server.takeRequest().getHeader("X-Request-Id")).isNotBlank();
        }
    }
    public static class Payload { public String value; }
}
