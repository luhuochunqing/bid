package com.xiyu.bid.ai.compliance;

import com.xiyu.bid.biddraftagent.infrastructure.openai.OpenAiBidAgentConfigurationResolver;
import com.xiyu.bid.biddraftagent.infrastructure.openai.OpenAiBidAgentRequestConfig;
import com.xiyu.bid.biddraftagent.infrastructure.openai.OpenAiStructuredOutputService;
import com.xiyu.bid.compliance.domain.DocumentContent;
import com.xiyu.bid.compliance.domain.PageImage;
import com.xiyu.bid.compliance.domain.policy.ScanPageTextPolicy;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpenAiDocumentPageVisionDetectorTest {
    @Test
    void adapterRejectsTranscriptionOfAnUnsentPage() {
        var outputService = mock(OpenAiStructuredOutputService.class);
        var resolver = mock(OpenAiBidAgentConfigurationResolver.class);
        var config = new OpenAiBidAgentRequestConfig("test", "http://localhost", "model", Duration.ofSeconds(1), null);
        when(resolver.resolve(anyString())).thenReturn(config);
        var output = new OpenAiDocumentPageVisionDetector.Output();
        output.pages = List.of(new ScanPageTextPolicy.Transcription(11, "真实转写文本", 0.95),
                new ScanPageTextPolicy.Transcription(90, "非送检页不得补写", 0.99));
        when(outputService.request(anyString(), anyList(), eq(OpenAiDocumentPageVisionDetector.Output.class),
                eq(config), anyString())).thenReturn(output);
        var document = new DocumentContent("bid.pdf", "hash", "正文", List.of(
                new DocumentContent.PageContent(10, "法定代表人身份证明", List.of()),
                new DocumentContent.PageContent(11, "11", List.of()),
                new DocumentContent.PageContent(90, "90", List.of())),
                List.of(new PageImage(1, "image/png", "page", 11, 100, 100, "aGk=")));
        assertThat(new OpenAiDocumentPageVisionDetector(outputService, resolver).transcribe(document))
                .extracting(ScanPageTextPolicy.Transcription::pageNumber).containsExactly(11);
    }
}
