package com.xiyu.bid.biddraftagent.infrastructure.openai;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.*;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import static org.assertj.core.api.Assertions.*;

class ActualScoringTransportBudgetTest {
    @Test void schemaFallbackAndOuterRetryShareAtMostTwoActualTransmissions()throws Exception {
        try(var server=new MockWebServer()) {
            server.start();
            server.enqueue(new MockResponse().setResponseCode(400).setBody("{\"error\":{\"message\":\"json_schema unsupported\"}}"));
            server.enqueue(new MockResponse().setResponseCode(503).setBody("{\"error\":{\"message\":\"unavailable\"}}"));
            var json=new ObjectMapper();var transport=new OpenAiSdkStructuredOutputTransport(json,new OpenAiJsonObjectPayloadReader(json));
            var budget=new OpenAiRequestBudget(System.currentTimeMillis()+5000);
            var config=new OpenAiBidAgentRequestConfig("synthetic",server.url("/v1").toString(),"synthetic",Duration.ofSeconds(3),OpenAiBidAgentApiStyle.CHAT_COMPLETIONS).forScoring(budget);
            assertThat(config.maxRetries()).isZero();
            assertThatThrownBy(()->transport.requestWithChatCompletions("synthetic",Payload.class,config)).isInstanceOf(RuntimeException.class);
            assertThatThrownBy(()->transport.requestWithChatCompletions("synthetic",Payload.class,config)).hasMessageContaining("SCORING_ATTEMPTS_EXHAUSTED");
            assertThat(server.getRequestCount()).isEqualTo(2);
            assertThat(budget.attempts()).isEqualTo(2);
        }
    }
    @Test void expiredItemRejectsConfigurationWithoutConsumingBudget() {
        var config=new OpenAiBidAgentRequestConfig("synthetic","http://provider.invalid/v1","synthetic",Duration.ofSeconds(3),OpenAiBidAgentApiStyle.CHAT_COMPLETIONS);
        var budget=new OpenAiRequestBudget(System.currentTimeMillis()-1);
        assertThatThrownBy(()->config.forScoring(budget))
                .hasMessageContaining("SCORING_DEADLINE_EXCEEDED");
        assertThat(budget.attempts()).isZero();
    }
    public static class Payload {public String value;}
}
