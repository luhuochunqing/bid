package com.xiyu.bid.biddraftagent.infrastructure.openai;

import org.junit.jupiter.api.Test;

import java.time.Duration;
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

        private <T> Optional<T> cast(Class<T> responseType) {
            if (payload == null) {
                return Optional.empty();
            }
            return Optional.of(responseType.cast(payload));
        }
    }
}
