package com.xiyu.bid.scoreparse.infrastructure.openai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.biddraftagent.infrastructure.openai.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PrimaryTextScoreRequestTest {
    @Test void sendsWholeDocumentOnceWithoutGeometryOrOverlappingChunks() {
        var output = mock(OpenAiStructuredOutputService.class);
        var resolver = mock(OpenAiBidAgentConfigurationResolver.class);
        var config = new OpenAiBidAgentRequestConfig("test", "https://invalid.example/v1", "boundary",
                java.time.Duration.ofSeconds(1), null);
        when(resolver.resolve(anyString())).thenReturn(config);
        var response = new OpenAiMultimodalScoreFactExtractor.Output();
        response.facts = List.of();
        var prompt = ArgumentCaptor.forClass(String.class);
        when(output.request(prompt.capture(), anyList(), eq(OpenAiMultimodalScoreFactExtractor.Output.class),
                eq(config), anyString())).thenReturn(response);
        var extractor = new OpenAiMultimodalScoreFactExtractor(output, resolver, new ObjectMapper());
        extractor.extractPrimaryTextAndProgress("HEAD" + "中".repeat(45000) + "TAIL", () -> {});
        assertThat(prompt.getAllValues()).hasSize(1);
        assertThat(prompt.getValue()).contains("HEAD", "TAIL");
    }
    @Test void oversizedInputIsNotTruncatedAndNeverCallsProvider() {
        var output = mock(OpenAiStructuredOutputService.class);
        var resolver = mock(OpenAiBidAgentConfigurationResolver.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> PrimaryTextScoreRequest.extract(
                "中".repeat(80001), () -> {}, output, resolver)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(output, resolver);
    }
}
