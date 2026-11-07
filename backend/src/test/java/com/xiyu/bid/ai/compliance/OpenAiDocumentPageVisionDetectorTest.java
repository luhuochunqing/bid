package com.xiyu.bid.ai.compliance;

import com.xiyu.bid.biddraftagent.infrastructure.openai.OpenAiBidAgentConfigurationResolver;
import com.xiyu.bid.biddraftagent.infrastructure.openai.OpenAiBidAgentRequestConfig;
import com.xiyu.bid.biddraftagent.infrastructure.openai.OpenAiStructuredOutputService;
import com.xiyu.bid.compliance.domain.DocumentContent;
import com.xiyu.bid.compliance.domain.PageImage;
import com.xiyu.bid.compliance.domain.policy.ScanPageTextPolicy;
import com.xiyu.bid.scoreparse.infrastructure.baidu.BaiduTableModels.BaiduTableCell;
import com.xiyu.bid.scoreparse.infrastructure.baidu.BaiduTableModels.BaiduTableItem;
import com.xiyu.bid.scoreparse.infrastructure.baidu.BaiduTableModels.BaiduTableResponse;
import com.xiyu.bid.scoreparse.infrastructure.baidu.BaiduTableModels.BaiduTableWord;
import com.xiyu.bid.scoreparse.infrastructure.baidu.BaiduTableOcrClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpenAiDocumentPageVisionDetectorTest {

    private static final String VALID_1X1_PNG_BASE64 =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==";

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
                List.of(new PageImage(1, "image/png", "page", 11, 100, 100, VALID_1X1_PNG_BASE64)));
        assertThat(new OpenAiDocumentPageVisionDetector(outputService, resolver).transcribe(document))
                .extracting(ScanPageTextPolicy.Transcription::pageNumber).containsExactly(11);
    }

    @Test
    @DisplayName("已配置的百度OCR通道启用时，优先通过BaiduTableOcrClient识别并合并同页多图文字")
    void baiduOcrChannelTranscribesAndMergesSamePageImagesFirst() throws Exception {
        var outputService = mock(OpenAiStructuredOutputService.class);
        var resolver = mock(OpenAiBidAgentConfigurationResolver.class);
        var baiduClient = mock(BaiduTableOcrClient.class);
        when(baiduClient.isEnabled()).thenReturn(true);

        var tableItem1 = new BaiduTableItem(
                List.of(new BaiduTableWord("授权委托书", List.of())),
                List.of(new BaiduTableWord("授权期限：自签署之日至投标有效期结束", List.of())),
                List.of(
                        new BaiduTableCell(0, 0, 0, 0, "法定代表人：王立军", List.of()),
                        new BaiduTableCell(0, 0, 1, 1, "委托代理人：张伟", List.of()),
                        new BaiduTableCell(1, 1, 0, 1, "代理权限：全权代表本公司参加投标", List.of())
                ),
                List.of());
        var tableItem2 = new BaiduTableItem(
                List.of(new BaiduTableWord("投标人承诺函", List.of())),
                List.of(),
                List.of(new BaiduTableCell(0, 0, 0, 0, "致：新兴际华集团有限公司：我方完全响应招标文件。", List.of())),
                List.of());
        when(baiduClient.extractTablesFromPdf(any(byte[].class), anyInt()))
                .thenReturn(new BaiduTableResponse(1, List.of(tableItem1), 0, null, 1L))
                .thenReturn(new BaiduTableResponse(1, List.of(tableItem2), 0, null, 2L));

        var document = new DocumentContent("bid.docx", "hash", "正文", List.of(
                new DocumentContent.PageContent(2, "授权委托书\n投标人承诺函", List.of())),
                List.of(
                        new PageImage(1, "image/png", "授权委托书", 2, 100, 100, VALID_1X1_PNG_BASE64),
                        new PageImage(2, "image/png", "投标人承诺函", 2, 100, 100, VALID_1X1_PNG_BASE64)
                ));

        var detector = new OpenAiDocumentPageVisionDetector(outputService, resolver, baiduClient);
        var transcriptions = detector.transcribe(document);

        assertThat(transcriptions).hasSize(1);
        assertThat(transcriptions.getFirst().pageNumber()).isEqualTo(2);
        assertThat(transcriptions.getFirst().text())
                .contains("授权委托书", "法定代表人：王立军", "投标人承诺函", "新兴际华集团有限公司");
        verify(outputService, never()).request(anyString(), anyList(), any(), any(), anyString());
    }

    @Test
    @DisplayName("同页多图中百度OCR单图未命中或抛异常时，未命中单图回退多模态通道并与成功结果合并")
    void samePagePartialBaiduFailureFallsBackToMultimodalPerImageAndMerges() throws Exception {
        var outputService = mock(OpenAiStructuredOutputService.class);
        var resolver = mock(OpenAiBidAgentConfigurationResolver.class);
        var baiduClient = mock(BaiduTableOcrClient.class);
        when(baiduClient.isEnabled()).thenReturn(true);

        var tableItem1 = new BaiduTableItem(
                List.of(new BaiduTableWord("授权委托书", List.of())),
                List.of(),
                List.of(new BaiduTableCell(0, 0, 0, 0, "法定代表人：王立军 委托代理人：张伟", List.of())),
                List.of());
        when(baiduClient.extractTablesFromPdf(any(byte[].class), anyInt()))
                .thenReturn(new BaiduTableResponse(1, List.of(tableItem1), 0, null, 1L))
                .thenThrow(new java.io.IOException("Baidu OCR timeout on image 2"));

        var config = new OpenAiBidAgentRequestConfig("test", "http://localhost", "model", Duration.ofSeconds(1), null);
        when(resolver.resolve(anyString())).thenReturn(config);
        var output = new OpenAiDocumentPageVisionDetector.Output();
        output.pages = List.of(new ScanPageTextPolicy.Transcription(
                2, "投标人承诺函 致：新兴际华集团有限公司", 0.92));
        when(outputService.request(anyString(), anyList(), eq(OpenAiDocumentPageVisionDetector.Output.class),
                eq(config), anyString())).thenReturn(output);

        var document = new DocumentContent("bid.docx", "hash", "正文", List.of(
                new DocumentContent.PageContent(2, "授权委托书\n投标人承诺函", List.of())),
                List.of(
                        new PageImage(1, "image/png", "授权委托书", 2, 100, 100, VALID_1X1_PNG_BASE64),
                        new PageImage(2, "image/png", "投标人承诺函", 2, 100, 100, VALID_1X1_PNG_BASE64)
                ));

        var detector = new OpenAiDocumentPageVisionDetector(outputService, resolver, baiduClient);
        var transcriptions = detector.transcribe(document);

        assertThat(transcriptions).hasSize(1);
        assertThat(transcriptions.getFirst().pageNumber()).isEqualTo(2);
        assertThat(transcriptions.getFirst().text())
                .contains("授权委托书", "法定代表人：王立军", "投标人承诺函", "新兴际华集团有限公司");
        assertThat(transcriptions.getFirst().confidence()).isEqualTo(0.92);
        verify(outputService).request(anyString(), anyList(), eq(OpenAiDocumentPageVisionDetector.Output.class),
                eq(config), anyString());
    }
}
