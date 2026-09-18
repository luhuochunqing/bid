package com.xiyu.bid.docinsight.infrastructure.extractor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.docinsight.application.ExtractedDocument;
import com.xiyu.bid.docinsight.application.exception.DocInsightException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarkItDownSidecarExtractorTest {

    @Mock
    private RestTemplate restTemplate;

    @Test
    void shouldSendSidecarKeyWhenConfigured() {
        MarkItDownSidecarExtractor extractor = new MarkItDownSidecarExtractor(
                restTemplate,
                new ObjectMapper(),
                "http://localhost:8000",
                "test-sidecar-key",
                30L
        );
        String sidecarResponse = """
                {
                  "documentId": "test.pdf",
                  "markdown": "# Header\\nContent",
                  "sections": [],
                  "warnings": [],
                  "converter": "markitdown",
                  "contentHash": "12345"
                }
                """;
        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(sidecarResponse);

        ExtractedDocument result = extractor.extract("test.pdf", "application/pdf", "dummy".getBytes());

        ArgumentCaptor<HttpEntity> requestCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForObject(anyString(), requestCaptor.capture(), eq(String.class));
        assertThat(result.extractorKey()).isEqualTo("markitdown-sidecar");
        assertThat(requestCaptor.getValue().getHeaders().getFirst("X-Sidecar-Key"))
                .isEqualTo("test-sidecar-key");
    }

    @Test
    void shouldExtractPlainTextWithoutSidecar() {
        MarkItDownSidecarExtractor extractor = new MarkItDownSidecarExtractor(
                restTemplate,
                new ObjectMapper(),
                "http://localhost:8000",
                "test-sidecar-key",
                30L
        );

        ExtractedDocument result = extractor.extract(
                "paste.txt",
                "text/plain; charset=UTF-8",
                "标题：南方电网供应链数字化项目".getBytes(StandardCharsets.UTF_8)
        );

        assertThat(result.text()).isEqualTo("标题：南方电网供应链数字化项目");
        assertThat(result.textLength()).isEqualTo(result.text().length());
        assertThat(result.extractorKey()).isEqualTo("plain-text");
        verify(restTemplate, never()).postForObject(anyString(), any(HttpEntity.class), eq(String.class));
    }

    @Test
    @org.junit.jupiter.api.DisplayName("根因回归：超大 content 超过转换上限 -> 抛受控异常而非 OOM（不得触发 sidecar POST/fallback）")
    void oversizedContent_throwsControlledExceptionInsteadOfOom() {
        MarkItDownSidecarExtractor extractor = new MarkItDownSidecarExtractor(
                restTemplate,
                new ObjectMapper(),
                "http://localhost:8000",
                "test-sidecar-key",
                30L   // 上限 30MB，与 sidecar SIDECAR_MAX_UPLOAD_MB 一致
        );

        // 超过 30MB 上限即可触发超限分支（无需 401MB 实体积，避免测试堆压力）——超限在进入任何大内存 POST/fallback 前兜住。
        byte[] oversized = new byte[31 * 1024 * 1024];
        // 填入一点可打印字符让 new String 有内容（但此分支根本不该走到 new String）
        oversized[0] = 'A';

        assertThatThrownBy(() -> extractor.extract(
                "张家口银行——投标6.16.docx", "application/octet-stream", oversized))
                .isInstanceOf(DocInsightException.class)
                .hasMessageContaining("超过转换上限")
                .hasMessageContaining("30MB");

        // 超限必须在进入任何网络请求前兜住——绝不能把超大 multipart 发出去或走 fallback new String
        verify(restTemplate, never()).postForObject(anyString(), any(HttpEntity.class), eq(String.class));
    }

    @Test
    @org.junit.jupiter.api.DisplayName("方案B回归：超限 DocInsightException 经 @Recover 原样抛出，不被包装成 ExhaustedRetryException")
    void docInsightException_recover_rethrowsOriginal() {
        MarkItDownSidecarExtractor extractor = new MarkItDownSidecarExtractor(
                restTemplate,
                new ObjectMapper(),
                "http://localhost:8000",
                "test-sidecar-key",
                30L
        );

        DocInsightException cause = new DocInsightException(0,
                "文档文件超过转换上限 30MB，无法提取文本: 大标书.docx（实际 382MB）");

        // @Recover(DocInsightException) 必须原样 throw 原始异常（非 fallback、非换抛），
        // 否则 RecoverAnnotationRecoveryHandler 匹配不到恢复方法，会把真实业务失败包装成
        // ExhaustedRetryException("Cannot locate recovery method") 掩埋根因。
        assertThatThrownBy(() -> extractor.recover(cause, "大标书.docx", "application/octet-stream",
                new byte[]{'x'}))
                .isSameAs(cause);

        // 确定性失败绝不降级为占位文本——不允许走任何 fallbackExtract
        verify(restTemplate, never()).postForObject(anyString(), any(HttpEntity.class), eq(String.class));
    }

    @Test
    @org.junit.jupiter.api.DisplayName("根因修复验证：Sidecar 不健康时，.docx 自动走 POI XWPF 降级并提取文字与表格")
    void docxContent_whenSidecarUnhealthy_fallsBackToPoiXwpf() throws Exception {
        MarkItDownSidecarExtractor extractor = new MarkItDownSidecarExtractor(
                restTemplate,
                new ObjectMapper(),
                "http://localhost:8000",
                "test-sidecar-key",
                30L
        );

        byte[] docxBytes;
        try (org.apache.poi.xwpf.usermodel.XWPFDocument doc = new org.apache.poi.xwpf.usermodel.XWPFDocument();
             java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream()) {
            org.apache.poi.xwpf.usermodel.XWPFParagraph p = doc.createParagraph();
            p.createRun().setText("招标文件评分标准说明");
            org.apache.poi.xwpf.usermodel.XWPFTable table = doc.createTable(2, 2);
            table.getRow(0).getCell(0).setText("评分因素");
            table.getRow(0).getCell(1).setText("分值");
            table.getRow(1).getCell(0).setText("服务方案");
            table.getRow(1).getCell(1).setText("30");
            doc.write(bos);
            docxBytes = bos.toByteArray();
        }

        when(restTemplate.getForObject("http://localhost:8000/health", String.class))
                .thenThrow(new org.springframework.web.client.RestClientException("Sidecar down"));

        ExtractedDocument result = extractor.extract(
                "中国电子招标文件.docx",
                "application/vnd.openxmlformats-officedocument.word",
                docxBytes
        );

        assertThat(result.extractorKey()).isEqualTo("poi-xwpf-fallback");
        assertThat(result.text()).contains("招标文件评分标准说明");
        assertThat(result.text()).contains("评分因素");
        assertThat(result.text()).contains("服务方案");
    }

    @Test
    @org.junit.jupiter.api.DisplayName("设计弯路修正：损毁的二进制文档在 Sidecar 宕机且 POI 失败时，Fail-Fast 抛出 DocInsightException 而非乱码")
    void corruptDocx_whenSidecarAndPoiFail_throwsDocInsightException() {
        MarkItDownSidecarExtractor extractor = new MarkItDownSidecarExtractor(
                restTemplate,
                new ObjectMapper(),
                "http://localhost:8000",
                "test-sidecar-key",
                30L
        );

        when(restTemplate.getForObject("http://localhost:8000/health", String.class))
                .thenThrow(new org.springframework.web.client.RestClientException("Sidecar down"));

        byte[] corruptBytes = "PK\u0003\u0004corrupted-docx-stream".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> extractor.extract(
                "破损招标文件.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                corruptBytes
        ))
                .isInstanceOf(DocInsightException.class)
                .hasMessageContaining("无法从损毁或不受支持的二进制文档提取内容");
    }
}
