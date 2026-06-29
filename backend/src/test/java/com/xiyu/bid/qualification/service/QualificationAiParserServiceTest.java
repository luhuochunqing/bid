package com.xiyu.bid.qualification.service;

import com.xiyu.bid.ai.client.AiProviderRuntimeConfig;
import com.xiyu.bid.ai.client.RoutingAiProvider;
import com.xiyu.bid.docinsight.application.DocumentTextExtractor;
import com.xiyu.bid.docinsight.application.ExtractedDocument;
import com.xiyu.bid.exception.BusinessException;
import com.xiyu.bid.qualification.dto.QualificationDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 验证资质证书 AI 解析服务在失败路径下不再返回 Mock 假数据，而是抛业务异常。
 *
 * 对应 SECURITY.md「禁止 Mock」政策：删除 getMockFallback() 后，三处失败路径
 * （文档提取失败 / AI 未配置 / AI 调用失败）必须以业务异常形式向上传递。
 */
class QualificationAiParserServiceTest {

    private DocumentTextExtractor documentTextExtractor;
    private RoutingAiProvider routingAiProvider;
    private RestTemplate restTemplate;
    private QualificationAiParserService service;

    @BeforeEach
    void setUp() {
        documentTextExtractor = mock(DocumentTextExtractor.class);
        routingAiProvider = mock(RoutingAiProvider.class);
        restTemplate = mock(RestTemplate.class);
        service = new QualificationAiParserService(
                documentTextExtractor, routingAiProvider, restTemplate, new ObjectMapper());
    }

    @Test
    @DisplayName("文档提取异常时应抛 BusinessException，不再返回 Mock 假数据")
    void extractFromPdf_whenDocumentExtractorThrows_shouldThrowBusinessException() {
        MultipartFile file = new MockMultipartFile("file", "cert.pdf", "application/pdf", new byte[]{1, 2, 3});
        when(documentTextExtractor.extract(anyString(), anyString(), any(byte[].class)))
                .thenThrow(new RuntimeException("sidecar down"));

        assertThatThrownBy(() -> service.extractFromPdf(file))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无法读取资质证书文件内容");
    }

    @Test
    @DisplayName("文档提取返回空文本时应抛 BusinessException，不再返回 Mock 假数据")
    void extractFromPdf_whenDocumentTextIsBlank_shouldThrowBusinessException() {
        MultipartFile file = new MockMultipartFile("file", "cert.pdf", "application/pdf", new byte[]{1});
        when(documentTextExtractor.extract(anyString(), anyString(), any(byte[].class)))
                .thenReturn(new ExtractedDocument("", 0, null, null, null));

        assertThatThrownBy(() -> service.extractFromPdf(file))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("文件内容为空");
    }

    @Test
    @DisplayName("AI Provider 未配置时应透传 IllegalStateException，不再吞异常返回 Mock 假数据")
    void extractFromPdf_whenAiProviderNotConfigured_shouldPropagateIllegalStateException() {
        MultipartFile file = new MockMultipartFile("file", "cert.pdf", "application/pdf", new byte[]{1});
        when(documentTextExtractor.extract(anyString(), anyString(), any(byte[].class)))
                .thenReturn(new ExtractedDocument("一些证书文本", 6, null, null, null));
        // RoutingAiProvider.resolveActiveConfig() 在 AI 未启用时抛 IllegalStateException
        when(routingAiProvider.resolveActiveConfig())
                .thenThrow(new IllegalStateException("AI 功能已在系统设置中关闭"));

        assertThatThrownBy(() -> service.extractFromPdf(file))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AI 功能已在系统设置中关闭");
    }

    @Test
    @DisplayName("AI 调用失败时应抛 BusinessException，不再返回 Mock 假数据")
    void extractFromPdf_whenAiCallFails_shouldThrowBusinessException() {
        MultipartFile file = new MockMultipartFile("file", "cert.pdf", "application/pdf", new byte[]{1});
        when(documentTextExtractor.extract(anyString(), anyString(), any(byte[].class)))
                .thenReturn(new ExtractedDocument("一些证书文本", 6, null, null, null));
        when(routingAiProvider.resolveActiveConfig())
                .thenReturn(new AiProviderRuntimeConfig("openai", "http://localhost:1234/v1/chat/completions", "model-x", "key"));
        when(restTemplate.exchange(anyString(), any(), any(), (Class<String>) any()))
                .thenThrow(new RuntimeException("network down"));

        assertThatThrownBy(() -> service.extractFromPdf(file))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("AI 解析资质证书失败");
    }

    @Test
    @DisplayName("AI 返回结果为空时应抛 BusinessException，不再返回 Mock 假数据")
    void extractFromPdf_whenAiReturnsNullBody_shouldThrowBusinessException() {
        MultipartFile file = new MockMultipartFile("file", "cert.pdf", "application/pdf", new byte[]{1});
        when(documentTextExtractor.extract(anyString(), anyString(), any(byte[].class)))
                .thenReturn(new ExtractedDocument("一些证书文本", 6, null, null, null));
        when(routingAiProvider.resolveActiveConfig())
                .thenReturn(new AiProviderRuntimeConfig("openai", "http://localhost:1234/v1/chat/completions", "model-x", "key"));

        org.springframework.http.ResponseEntity<String> resp = new org.springframework.http.ResponseEntity<>(null, org.springframework.http.HttpStatus.OK);
        when(restTemplate.exchange(anyString(), any(), any(), (Class<String>) any())).thenReturn(resp);

        assertThatThrownBy(() -> service.extractFromPdf(file))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("AI 返回结果为空");
    }

    @Test
    @DisplayName("AI 解析成功时应返回真实解析结果（不再走 Mock 路径）")
    void extractFromPdf_whenAiReturnsValidJson_shouldReturnParsedDto() {
        MultipartFile file = new MockMultipartFile("file", "cert.pdf", "application/pdf", new byte[]{1});
        when(documentTextExtractor.extract(anyString(), anyString(), any(byte[].class)))
                .thenReturn(new ExtractedDocument("一些证书文本", 6, null, null, null));
        when(routingAiProvider.resolveActiveConfig())
                .thenReturn(new AiProviderRuntimeConfig("openai", "http://localhost:1234/v1/chat/completions", "model-x", "key"));

        String aiJson = "{\"name\":\"ISO9001\",\"certificateNo\":\"CN-001\",\"issuer\":\"SGS\",\"holderName\":\"西域\",\"expiryDate\":\"2027-01-01\"}";
        org.springframework.http.ResponseEntity<String> resp =
                new org.springframework.http.ResponseEntity<>(aiJson, org.springframework.http.HttpStatus.OK);
        when(restTemplate.exchange(anyString(), any(), any(), (Class<String>) any())).thenReturn(resp);

        QualificationDTO dto = service.extractFromPdf(file);

        assertThat(dto.getName()).isEqualTo("ISO9001");
        assertThat(dto.getCertificateNo()).isEqualTo("CN-001");
        assertThat(dto.getIssuer()).isEqualTo("SGS");
        assertThat(dto.getHolderName()).isEqualTo("西域");
        assertThat(dto.getExpiryDate()).isEqualTo(java.time.LocalDate.of(2027, 1, 1));
    }
}
