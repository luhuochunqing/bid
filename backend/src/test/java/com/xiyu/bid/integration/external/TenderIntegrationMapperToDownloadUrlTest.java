package com.xiyu.bid.integration.external;

import com.xiyu.bid.file.application.ObsShareUrlSigner;
import com.xiyu.bid.tender.dto.TenderAttachmentDTO;
import com.xiyu.bid.tender.dto.TenderDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * TenderIntegrationMapper URL 转换单元测试。
 *
 * <p>覆盖两条路径：
 * <ul>
 *   <li>{@code toDownloadUrl} / {@code toFullUrl}：XiYu 内部下载端点（/api/doc-insight/download），需要登录态</li>
 *   <li>{@code toIntegrationDownloadUrl} / {@code toIntegrationFullUrl}：CRM 集成下载端点（/api/integration/tenders/attachments/download），公开访问</li>
 * </ul>
 * 详见 CO-280 403 修复。Resolver 已改构造器注入，测试直接构造实例（无 publicBaseUrl 用 resolver，配置了用 resolverWithBase）。
 */
@DisplayName("TenderIntegrationMapper URL 转换")
class TenderIntegrationMapperToDownloadUrlTest {

    private static final String BASE = "https://winbid-test.ehsy.com";

    /** 同源部署（未配置 publicBaseUrl）的 Resolver。 */
    private final TenderAttachmentUrlResolver resolver =
            new TenderAttachmentUrlResolver(new IntegrationEndpointUrls(""), null);

    /** 配置了 publicBaseUrl 的 Resolver（跨域访问场景）。 */
    private final TenderAttachmentUrlResolver resolverWithBase =
            new TenderAttachmentUrlResolver(new IntegrationEndpointUrls(BASE), null);

    // ── toDownloadUrl（XiYu 内部端点 /api/doc-insight/download）─────────────────

    @Test
    @DisplayName("toDownloadUrl 未配置 publicBaseUrl 时返回相对路径（同源部署）")
    void toDownloadUrl_noPublicBaseUrl_returnsRelativePath() {
        String result = resolver.toDownloadUrl("doc-insight://TENDER_INTAKE/test.pdf");
        assertThat(result).startsWith("/api/doc-insight/download?fileUrl=");
    }

    @Test
    @DisplayName("toDownloadUrl 配置 publicBaseUrl 时返回完整 URL（跨域访问）")
    void toDownloadUrl_withPublicBaseUrl_returnsFullUrl() {
        String result = resolverWithBase.toDownloadUrl("doc-insight://TENDER_INTAKE/test.pdf");
        assertThat(result).startsWith("https://winbid-test.ehsy.com/api/doc-insight/download?fileUrl=");
    }

    @Test
    @DisplayName("toDownloadUrl doc-insight:// URL 被正确 URL 编码")
    void toDownloadUrl_docInsightUrl_isUrlEncoded() {
        String result = resolver.toDownloadUrl("doc-insight://TENDER_INTAKE/create-tender/test.docx");
        assertThat(result).contains("doc-insight%3A%2F%2F");
    }

    @Test
    @DisplayName("toDownloadUrl 已是 /api/... 下载地址的不再二次包装（CO-283 幂等）")
    void toDownloadUrl_alreadyDownloadUrl_isIdempotent() {
        String input = "/api/doc-insight/download?fileUrl=doc-insight%3A%2F%2Ftest.pdf";
        String result = resolverWithBase.toDownloadUrl(input);
        // 应只补全域名，不二次编码
        assertThat(result).isEqualTo("https://winbid-test.ehsy.com/api/doc-insight/download?fileUrl=doc-insight%3A%2F%2Ftest.pdf");
    }

    @Test
    @DisplayName("toDownloadUrl http(s):// 外部 URL 原样返回，不包装")
    void toDownloadUrl_httpUrl_returnsAsIs() {
        String input = "https://image-c.ehsy.com/files/test.pdf";
        String result = resolver.toDownloadUrl(input);
        assertThat(result).isEqualTo(input);
    }

    @Test
    @DisplayName("toDownloadUrl null 或空字符串原样返回")
    void toDownloadUrl_nullOrBlank_returnsAsIs() {
        assertThat(resolver.toDownloadUrl(null)).isNull();
        assertThat(resolver.toDownloadUrl("")).isEmpty();
        assertThat(resolver.toDownloadUrl("   ")).isEqualTo("   ");
    }

    // ── toFullUrl（XiYu 内部端点）──────────────────────────────────────────────

    @Test
    @DisplayName("toFullUrl 处理 doc-insight:// 前缀")
    void toFullUrl_docInsightUrl_returnsDownloadUrl() {
        String result = resolverWithBase.toFullUrl("doc-insight://TENDER_INTAKE/test.pdf");
        assertThat(result).startsWith("https://winbid-test.ehsy.com/api/doc-insight/download?fileUrl=");
    }

    @Test
    @DisplayName("toFullUrl 处理 /api/... 相对路径，补全域名")
    void toFullUrl_relativeApiPath_prependsPublicBaseUrl() {
        String result = resolverWithBase.toFullUrl("/api/doc-insight/download?fileUrl=doc-insight%3A%2F%2Ftest");
        assertThat(result).isEqualTo("https://winbid-test.ehsy.com/api/doc-insight/download?fileUrl=doc-insight%3A%2F%2Ftest");
    }

    @Test
    @DisplayName("toFullUrl 处理 /api/... 相对路径，未配置 publicBaseUrl 时原样返回")
    void toFullUrl_relativeApiPath_noPublicBaseUrl_returnsAsIs() {
        String result = resolver.toFullUrl("/api/doc-insight/download?fileUrl=test");
        assertThat(result).isEqualTo("/api/doc-insight/download?fileUrl=test");
    }

    @Test
    @DisplayName("toFullUrl 处理 http(s):// 完整 URL，原样返回")
    void toFullUrl_httpUrl_returnsAsIs() {
        String result = resolverWithBase.toFullUrl("https://image-c.ehsy.com/files/test.pdf");
        assertThat(result).isEqualTo("https://image-c.ehsy.com/files/test.pdf");
    }

    @Test
    @DisplayName("toFullUrl 处理 null 值")
    void toFullUrl_null_returnsNull() {
        String result = resolver.toFullUrl(null);
        assertThat(result).isNull();
    }

    // ── toIntegrationDownloadUrl（CRM 集成端点 /api/integration/tenders/attachments/download）──

    @Test
    @DisplayName("toIntegrationDownloadUrl 未配置 publicBaseUrl 时返回相对路径")
    void toIntegrationDownloadUrl_noPublicBaseUrl_returnsRelativePath() {
        String result = resolver.toIntegrationDownloadUrl("doc-insight://TENDER_INTAKE/test.pdf");
        assertThat(result).startsWith("/api/integration/tenders/attachments/download?fileUrl=");
    }

    @Test
    @DisplayName("toIntegrationDownloadUrl 配置 publicBaseUrl 时返回完整 URL")
    void toIntegrationDownloadUrl_withPublicBaseUrl_returnsFullUrl() {
        String result = resolverWithBase.toIntegrationDownloadUrl("doc-insight://TENDER_INTAKE/test.pdf");
        assertThat(result).startsWith("https://winbid-test.ehsy.com/api/integration/tenders/attachments/download?fileUrl=");
    }

    @Test
    @DisplayName("toIntegrationDownloadUrl doc-insight:// URL 被正确 URL 编码")
    void toIntegrationDownloadUrl_docInsightUrl_isUrlEncoded() {
        String result = resolver.toIntegrationDownloadUrl("doc-insight://TENDER_INTAKE/create-tender/test.docx");
        assertThat(result).contains("doc-insight%3A%2F%2F");
    }

    @Test
    @DisplayName("toIntegrationDownloadUrl CO-280 403 修复：旧 /api/doc-insight/download? URL 重定向到新集成端点")
    void toIntegrationDownloadUrl_legacyDocInsightUrl_redirectsToNewEndpoint() {
        String input = "/api/doc-insight/download?fileUrl=doc-insight%3A%2F%2Ftest.pdf";
        String result = resolverWithBase.toIntegrationDownloadUrl(input);
        assertThat(result).isEqualTo("https://winbid-test.ehsy.com/api/integration/tenders/attachments/download?fileUrl=doc-insight%3A%2F%2Ftest.pdf");
    }

    @Test
    @DisplayName("toIntegrationDownloadUrl 已是集成下载地址的不再二次包装（幂等）")
    void toIntegrationDownloadUrl_alreadyIntegrationUrl_isIdempotent() {
        String input = "/api/integration/tenders/attachments/download?fileUrl=doc-insight%3A%2F%2Ftest.pdf";
        String result = resolverWithBase.toIntegrationDownloadUrl(input);
        assertThat(result).isEqualTo("https://winbid-test.ehsy.com/api/integration/tenders/attachments/download?fileUrl=doc-insight%3A%2F%2Ftest.pdf");
    }

    @Test
    @DisplayName("toIntegrationFullUrl 已是集成下载地址且有 apiKey 时追加 api_key")
    void toIntegrationFullUrl_alreadyIntegrationUrlWithApiKey_appendsApiKey() {
        String input = "/api/integration/tenders/attachments/download?fileUrl=doc-insight%3A%2F%2Ftest.pdf";
        String result = resolver.toIntegrationFullUrl(input, "xiyu_sk_test_key");
        assertThat(result).isEqualTo("/api/integration/tenders/attachments/download?fileUrl=doc-insight%3A%2F%2Ftest.pdf&api_key=xiyu_sk_test_key");
    }

    @Test
    @DisplayName("toIntegrationDownloadUrl http(s):// 外部 URL 原样返回")
    void toIntegrationDownloadUrl_httpUrl_returnsAsIs() {
        String input = "https://image-c.ehsy.com/files/test.pdf";
        String result = resolver.toIntegrationDownloadUrl(input);
        assertThat(result).isEqualTo(input);
    }

    @Test
    @DisplayName("toIntegrationDownloadUrl null 或空字符串原样返回")
    void toIntegrationDownloadUrl_nullOrBlank_returnsAsIs() {
        assertThat(resolver.toIntegrationDownloadUrl(null)).isNull();
        assertThat(resolver.toIntegrationDownloadUrl("")).isEmpty();
    }

    // ── toIntegrationFullUrl（CRM 集成端点）─────────────────────────────────────

    @Test
    @DisplayName("toIntegrationFullUrl 处理 doc-insight:// 前缀")
    void toIntegrationFullUrl_docInsightUrl_returnsIntegrationDownloadUrl() {
        String result = resolverWithBase.toIntegrationFullUrl("doc-insight://TENDER_INTAKE/test.pdf");
        assertThat(result).startsWith("https://winbid-test.ehsy.com/api/integration/tenders/attachments/download?fileUrl=");
    }

    @Test
    @DisplayName("toIntegrationFullUrl CO-280 403 修复：旧 /api/doc-insight/download? 重定向到新端点")
    void toIntegrationFullUrl_legacyDocInsightUrl_redirectsToNewEndpoint() {
        String result = resolverWithBase.toIntegrationFullUrl("/api/doc-insight/download?fileUrl=doc-insight%3A%2F%2Ftest");
        assertThat(result).isEqualTo("https://winbid-test.ehsy.com/api/integration/tenders/attachments/download?fileUrl=doc-insight%3A%2F%2Ftest");
    }

    @Test
    @DisplayName("toIntegrationFullUrl CO-303 修复：完整内部下载 URL 重定向到集成端点并追加 api_key")
    void toIntegrationFullUrl_absoluteLegacyDocInsightUrlWithApiKey_redirectsToNewEndpoint() {
        String input = "https://winbid-test.ehsy.com/api/doc-insight/download?fileUrl=doc-insight%3A%2F%2Ftest.pdf";
        String result = resolverWithBase.toIntegrationFullUrl(input, "xiyu_sk_test_key");
        assertThat(result).isEqualTo("https://winbid-test.ehsy.com/api/integration/tenders/attachments/download?fileUrl=doc-insight%3A%2F%2Ftest.pdf&api_key=xiyu_sk_test_key");
    }

    @Test
    @DisplayName("toIntegrationFullUrl 处理 http(s):// 完整 URL，原样返回")
    void toIntegrationFullUrl_httpUrl_returnsAsIs() {
        String result = resolverWithBase.toIntegrationFullUrl("https://image-c.ehsy.com/files/test.pdf");
        assertThat(result).isEqualTo("https://image-c.ehsy.com/files/test.pdf");
    }

    @Test
    @DisplayName("toIntegrationFullUrl 外部 URL 即使包含集成下载路径也不追加 api_key")
    void toIntegrationFullUrl_externalIntegrationLikeUrlWithApiKey_returnsAsIs() {
        String input = "https://external.example/api/integration/tenders/attachments/download?fileUrl=x";
        String result = resolverWithBase.toIntegrationFullUrl(input, "xiyu_sk_test_key");
        assertThat(result).isEqualTo(input);
    }

    @Test
    @DisplayName("toIntegrationFullUrl 处理 null 值")
    void toIntegrationFullUrl_null_returnsNull() {
        String result = resolver.toIntegrationFullUrl(null);
        assertThat(result).isNull();
    }

    // ── normalizeFileUrls（CRM 集成专用，使用新端点）────────────────────────────

    @Test
    @DisplayName("normalizeFileUrls 同时处理 doc-insight:// 和 /api/... 格式，全部指向集成端点")
    void normalizeFileUrls_handlesBothFormats() {
        TenderDTO dto = new TenderDTO();
        dto.setSourceDocumentFileUrl("doc-insight://TENDER_INTAKE/source.docx");
        dto.setBidNoticeFileUrl("/api/doc-insight/download?fileUrl=doc-insight%3A%2F%2Fnotice.docx");
        dto.setAttachments(java.util.List.of(
                TenderAttachmentDTO.builder()
                        .fileName("test.pdf")
                        .fileType("pdf")
                        .fileUrl("doc-insight://TENDER_INTAKE/test.pdf")
                        .build()
        ));

        // 同包直接调用包私有方法（无需反射）
        TenderIntegrationMapper mapper = new TenderIntegrationMapper(null, null, null, resolverWithBase);
        mapper.normalizeFileUrls(dto);

        assertThat(dto.getSourceDocumentFileUrl())
                .startsWith("https://winbid-test.ehsy.com/api/integration/tenders/attachments/download?fileUrl=");
        assertThat(dto.getBidNoticeFileUrl())
                .startsWith("https://winbid-test.ehsy.com/api/integration/tenders/attachments/download?");
        assertThat(dto.getAttachments().get(0).getFileUrl())
                .startsWith("https://winbid-test.ehsy.com/api/integration/tenders/attachments/download?fileUrl=");
    }

    // ── obs-direct: 前缀 URL 转换（外部系统 → 集成下载端点，永久有效；内部用户 → 预签名）──

    @Test
    @DisplayName("obs-direct:{uploadId} 未配置 publicBaseUrl 时包装为相对路径集成端点（不依赖 signer）")
    void toIntegrationDownloadUrl_obsDirect_wrapsAsIntegrationEndpoint() {
        String result = resolver.toIntegrationDownloadUrl("obs-direct:upload-abc-123");
        assertThat(result).isEqualTo("/api/integration/tenders/attachments/download?fileUrl=obs-direct%3Aupload-abc-123");
    }

    @Test
    @DisplayName("obs-direct:{uploadId} 配置了 publicBaseUrl 时返回完整集成端点 URL（永久有效，非预签名）")
    void toIntegrationDownloadUrl_obsDirectWithPublicBaseUrl_returnsFullEndpointUrl() {
        String result = resolverWithBase.toIntegrationDownloadUrl("obs-direct:upload-abc-123");
        assertThat(result).isEqualTo(
                "https://winbid-test.ehsy.com/api/integration/tenders/attachments/download?fileUrl=obs-direct%3Aupload-abc-123");
        assertThat(result).doesNotContain("Signature=");
    }

    @Test
    @DisplayName("obs-direct:{uploadId} toIntegrationFullUrl 同样包装为集成端点 URL")
    void toIntegrationFullUrl_obsDirect_returnsIntegrationEndpoint() {
        String result = resolverWithBase.toIntegrationFullUrl("obs-direct:upload-abc-123");
        assertThat(result).isEqualTo(
                "https://winbid-test.ehsy.com/api/integration/tenders/attachments/download?fileUrl=obs-direct%3Aupload-abc-123");
    }

    @Test
    @DisplayName("obs-direct:{uploadId} 有 apiKey 时包装为集成端点并追加 api_key")
    void toIntegrationFullUrl_obsDirectWithApiKey_appendsApiKey() {
        String result = resolverWithBase.toIntegrationFullUrl("obs-direct:upload-abc-123", "xiyu_sk_test_key");
        assertThat(result).isEqualTo(
                "https://winbid-test.ehsy.com/api/integration/tenders/attachments/download?fileUrl=obs-direct%3Aupload-abc-123&api_key=xiyu_sk_test_key");
    }

    @Test
    @DisplayName("obs-direct:{uploadId} toDownloadUrl（内部用户）仍走 OBS 预签名路径")
    void toDownloadUrl_obsDirectWithSigner_returnsPresignedUrl() {
        ObsShareUrlSigner mockSigner = mock(ObsShareUrlSigner.class);
        when(mockSigner.trySign("obs-direct:upload-abc-123"))
                .thenReturn(Optional.of("https://widbid-obs.ehsy.com/bids/test.pdf?Signature=yyy"));
        TenderAttachmentUrlResolver resolverWithSigner =
                new TenderAttachmentUrlResolver(new IntegrationEndpointUrls(""), mockSigner);

        String result = resolverWithSigner.toDownloadUrl("obs-direct:upload-abc-123");
        assertThat(result).startsWith("https://widbid-obs.ehsy.com/");
    }
}

