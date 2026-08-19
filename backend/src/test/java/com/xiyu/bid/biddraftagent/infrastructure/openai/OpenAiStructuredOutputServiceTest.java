package com.xiyu.bid.biddraftagent.infrastructure.openai;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenAiStructuredOutputServiceTest {

    @Test
    void request_shouldUseResponsesTransportWhenConfigRequiresResponses() {
        RecordingTransport transport = new RecordingTransport(new Payload("responses"));
        OpenAiStructuredOutputService service = new OpenAiStructuredOutputService(transport);

        Payload payload = service.request(
                "analyze",
                Payload.class,
                config(OpenAiBidAgentApiStyle.RESPONSES),
                "missing"
        );

        assertThat(payload.value).isEqualTo("responses");
        assertThat(transport.responsesCallCount).isEqualTo(1);
        assertThat(transport.chatCallCount).isZero();
    }

    @Test
    void request_shouldUseChatTransportWhenConfigRequiresChatCompletions() {
        RecordingTransport transport = new RecordingTransport(new Payload("chat"));
        OpenAiStructuredOutputService service = new OpenAiStructuredOutputService(transport);

        Payload payload = service.request(
                "draft",
                Payload.class,
                config(OpenAiBidAgentApiStyle.CHAT_COMPLETIONS),
                "missing"
        );

        assertThat(payload.value).isEqualTo("chat");
        assertThat(transport.chatCallCount).isEqualTo(1);
        assertThat(transport.responsesCallCount).isZero();
    }

    @Test
    void request_shouldThrowConfiguredMessageWhenTransportReturnsEmpty() {
        RecordingTransport transport = new RecordingTransport(null);
        OpenAiStructuredOutputService service = new OpenAiStructuredOutputService(transport);

        assertThatThrownBy(() -> service.request(
                "draft",
                Payload.class,
                config(OpenAiBidAgentApiStyle.CHAT_COMPLETIONS),
                "structured output missing"
        )).isInstanceOf(IllegalStateException.class)
                .hasMessage("structured output missing");
    }

    @Test
    void multimodalRequest_shouldUseMultimodalTransportWhenImagesPresent() {
        RecordingTransport transport = new RecordingTransport(new Payload("multimodal"));
        OpenAiStructuredOutputService service = new OpenAiStructuredOutputService(transport);
        MultimodalImage image = new MultimodalImage("image/png", "aGk=");

        Payload payload = service.request(
                "score",
                List.of(image),
                Payload.class,
                config(OpenAiBidAgentApiStyle.CHAT_COMPLETIONS),
                "missing"
        );

        assertThat(payload.value).isEqualTo("multimodal");
        assertThat(transport.multimodalCallCount).isEqualTo(1);
        assertThat(transport.chatCallCount).isZero();
        assertThat(transport.responsesCallCount).isZero();
        assertThat(transport.lastImages).containsExactly(image);
    }

    @Test
    void multimodalRequest_shouldDelegateToTextPathWhenImagesEmpty() {
        RecordingTransport transport = new RecordingTransport(new Payload("text"));
        OpenAiStructuredOutputService service = new OpenAiStructuredOutputService(transport);

        Payload payload = service.request(
                "score",
                List.of(),
                Payload.class,
                config(OpenAiBidAgentApiStyle.CHAT_COMPLETIONS),
                "missing"
        );

        assertThat(payload.value).isEqualTo("text");
        assertThat(transport.chatCallCount).isEqualTo(1);
        assertThat(transport.multimodalCallCount).isZero();
    }

    @Test
    void multimodalRequest_shouldRejectResponsesApiStyleWithImages() {
        RecordingTransport transport = new RecordingTransport(new Payload("x"));
        OpenAiStructuredOutputService service = new OpenAiStructuredOutputService(transport);

        assertThatThrownBy(() -> service.request(
                "score",
                List.of(new MultimodalImage("image/png", "aGk=")),
                Payload.class,
                config(OpenAiBidAgentApiStyle.RESPONSES),
                "missing"
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RESPONSES");
    }

    @Test
    void multimodalRequest_shouldThrowConfiguredMessageWhenMultimodalTransportReturnsEmpty() {
        RecordingTransport transport = new RecordingTransport(null);
        OpenAiStructuredOutputService service = new OpenAiStructuredOutputService(transport);

        assertThatThrownBy(() -> service.request(
                "score",
                List.of(new MultimodalImage("image/png", "aGk=")),
                Payload.class,
                config(OpenAiBidAgentApiStyle.CHAT_COMPLETIONS),
                "multimodal output missing"
        )).isInstanceOf(IllegalStateException.class)
                .hasMessage("multimodal output missing");
    }

    private OpenAiBidAgentRequestConfig config(OpenAiBidAgentApiStyle apiStyle) {
        return new OpenAiBidAgentRequestConfig(
                "sk-test",
                "https://api.example.test/v1",
                "gpt-test",
                Duration.ofSeconds(30),
                apiStyle
        );
    }

    static final class Payload {
        public String value;

        Payload(String value) {
            this.value = value;
        }
    }

    static final class RecordingTransport implements OpenAiStructuredOutputTransport {
        private final Payload payload;
        private int responsesCallCount;
        private int chatCallCount;
        private int multimodalCallCount;
        private List<MultimodalImage> lastImages;

        RecordingTransport(Payload payload) {
            this.payload = payload;
        }

        @Override
        public <T> Optional<T> requestWithResponses(
                String prompt,
                Class<T> responseType,
                OpenAiBidAgentRequestConfig config
        ) {
            responsesCallCount++;
            return cast(responseType);
        }

        @Override
        public <T> Optional<T> requestWithChatCompletions(
                String prompt,
                Class<T> responseType,
                OpenAiBidAgentRequestConfig config
        ) {
            chatCallCount++;
            return cast(responseType);
        }

        @Override
        public <T> Optional<T> requestWithChatCompletionsMultimodal(
                String prompt,
                List<MultimodalImage> images,
                Class<T> responseType,
                OpenAiBidAgentRequestConfig config
        ) {
            multimodalCallCount++;
            lastImages = images;
            return cast(responseType);
        }

        private <T> Optional<T> cast(Class<T> responseType) {
            if (payload == null) {
                return Optional.empty();
            }
            return Optional.of(responseType.cast(payload));
        }
    }
}
