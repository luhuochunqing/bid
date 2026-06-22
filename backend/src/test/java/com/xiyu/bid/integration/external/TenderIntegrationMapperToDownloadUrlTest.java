package com.xiyu.bid.integration.external;

import com.xiyu.bid.tender.dto.TenderAttachmentDTO;
import com.xiyu.bid.tender.dto.TenderDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TenderIntegrationMapper URL 转换单元测试。
 *
 * <p>覆盖两条路径：
 * <ul>
 *   <li>{@code toDownloadUrl} / {@code toFullUrl}：XiYu 内部下载端点（/api/doc-insight/download），需要登录态</li>
 *   <li>{@code toIntegrationDownloadUrl} / {@code toIntegrationFullUrl}：CRM 集成下载端点（/api/integration/tenders/attachments/download），走 X-API-Key</li>
 * </ul>
 * 详见 CO-280 403 修复。
 */
@DisplayName("TenderIntegrationMapper URL 转换")
class TenderIntegrationMapperToDownloadUrlTest {

    @BeforeEach
    void resetPublicBaseUrl() throws Exception {
        setPublicBaseUrl(null);
    }

    @AfterEach
    void cleanup() throws Exception {
        setPublicBaseUrl(null);
    }

    // ── toDownloadUrl（XiYu 内部端点 /api/doc-insight/download）─────────────────

    @Test
    @DisplayName("toDownloadUrl 未配置 publicBaseUrl 时返回相对路径（同源部署）")
    void toDownloadUrl_noPublicBaseUrl_returnsRelativePath() {
        String result = TenderIntegrationMapper.toDownloadUrl("doc-insight://TENDER_INTAKE/test.pdf");
        assertThat(result).startsWith("/api/doc-insight/download?fileUrl=");
    }

    @Test
    @DisplayName("toDownloadUrl 配置 publicBaseUrl 时返回完整 URL（跨域访问）")
    void toDownloadUrl_withPublicBaseUrl_returnsFullUrl() throws Exception {
        setPublicBaseUrl("https://winbid-test.ehsy.com");
        String result = TenderIntegrationMapper.toDownloadUrl("doc-insight://TENDER_INTAKE/test.pdf");
        assertThat(result).startsWith("https://winbid-test.ehsy.com/api/doc-insight/download?fileUrl=");
    }

    @Test
    @DisplayName("toDownloadUrl doc-insight:// URL 被正确 URL 编码")
    void toDownloadUrl_docInsightUrl_isUrlEncoded() {
        String result = TenderIntegrationMapper.toDownloadUrl("doc-insight://TENDER_INTAKE/create-tender/test.docx");
        assertThat(result).contains("doc-insight%3A%2F%2F");
    }

    @Test
    @DisplayName("toDownloadUrl 已是 /api/... 下载地址的不再二次包装（CO-283 幂等）")
    void toDownloadUrl_alreadyDownloadUrl_isIdempotent() throws Exception {
        setPublicBaseUrl("https://winbid-test.ehsy.com");
        String input = "/api/doc-insight/download?fileUrl=doc-insight%3A%2F%2Ftest.pdf";
        String result = TenderIntegrationMapper.toDownloadUrl(input);
        // 应只补全域名，不二次编码
        assertThat(result).isEqualTo("https://winbid-test.ehsy.com/api/doc-insight/download?fileUrl=doc-insight%3A%2F%2Ftest.pdf");
    }

    @Test
    @DisplayName("toDownloadUrl http(s):// 外部 URL 原样返回，不包装")
    void toDownloadUrl_httpUrl_returnsAsIs() {
        String input = "https://image-c.ehsy.com/files/test.pdf";
        String result = TenderIntegrationMapper.toDownloadUrl(input);
        assertThat(result).isEqualTo(input);
    }

    @Test
    @DisplayName("toDownloadUrl null 或空字符串原样返回")
    void toDownloadUrl_nullOrBlank_returnsAsIs() {
        assertThat(TenderIntegrationMapper.toDownloadUrl(null)).isNull();
        assertThat(TenderIntegrationMapper.toDownloadUrl("")).isEmpty();
        assertThat(TenderIntegrationMapper.toDownloadUrl("   ")).isEqualTo("   ");
    }

    // ── toFullUrl（XiYu 内部端点）──────────────────────────────────────────────

    @Test
    @DisplayName("toFullUrl 处理 doc-insight:// 前缀")
    void toFullUrl_docInsightUrl_returnsDownloadUrl() throws Exception {
        setPublicBaseUrl("https://winbid-test.ehsy.com");
        String result = TenderAttachmentUrlResolver.toFullUrl("doc-insight://TENDER_INTAKE/test.pdf");
        assertThat(result).startsWith("https://winbid-test.ehsy.com/api/doc-insight/download?fileUrl=");
    }

    @Test
    @DisplayName("toFullUrl 处理 /api/... 相对路径，补全域名")
    void toFullUrl_relativeApiPath_prependsPublicBaseUrl() throws Exception {
        setPublicBaseUrl("https://winbid-test.ehsy.com");
        String result = TenderAttachmentUrlResolver.toFullUrl("/api/doc-insight/download?fileUrl=doc-insight%3A%2F%2Ftest");
        assertThat(result).isEqualTo("https://winbid-test.ehsy.com/api/doc-insight/download?fileUrl=doc-insight%3A%2F%2Ftest");
    }

    @Test
    @DisplayName("toFullUrl 处理 /api/... 相对路径，未配置 publicBaseUrl 时原样返回")
    void toFullUrl_relativeApiPath_noPublicBaseUrl_returnsAsIs() {
        String result = TenderAttachmentUrlResolver.toFullUrl("/api/doc-insight/download?fileUrl=test");
        assertThat(result).isEqualTo("/api/doc-insight/download?fileUrl=test");
    }

    @Test
    @DisplayName("toFullUrl 处理 http(s):// 完整 URL，原样返回")
    void toFullUrl_httpUrl_returnsAsIs() throws Exception {
        setPublicBaseUrl("https://winbid-test.ehsy.com");
        String result = TenderAttachmentUrlResolver.toFullUrl("https://image-c.ehsy.com/files/test.pdf");
        assertThat(result).isEqualTo("https://image-c.ehsy.com/files/test.pdf");
    }

    @Test
    @DisplayName("toFullUrl 处理 null 值")
    void toFullUrl_null_returnsNull() {
        String result = TenderAttachmentUrlResolver.toFullUrl(null);
        assertThat(result).isNull();
    }

    // ── toIntegrationDownloadUrl（CRM 集成端点 /api/integration/tenders/attachments/download）──

    @Test
    @DisplayName("toIntegrationDownloadUrl 未配置 publicBaseUrl 时返回相对路径")
    void toIntegrationDownloadUrl_noPublicBaseUrl_returnsRelativePath() {
        String result = TenderAttachmentUrlResolver.toIntegrationDownloadUrl("doc-insight://TENDER_INTAKE/test.pdf");
        assertThat(result).startsWith("/api/integration/tenders/attachments/download?fileUrl=");
    }

    @Test
    @DisplayName("toIntegrationDownloadUrl 配置 publicBaseUrl 时返回完整 URL")
    void toIntegrationDownloadUrl_withPublicBaseUrl_returnsFullUrl() throws Exception {
        setPublicBaseUrl("https://winbid-test.ehsy.com");
        String result = TenderAttachmentUrlResolver.toIntegrationDownloadUrl("doc-insight://TENDER_INTAKE/test.pdf");
        assertThat(result).startsWith("https://winbid-test.ehsy.com/api/integration/tenders/attachments/download?fileUrl=");
    }

    @Test
    @DisplayName("toIntegrationDownloadUrl doc-insight:// URL 被正确 URL 编码")
    void toIntegrationDownloadUrl_docInsightUrl_isUrlEncoded() {
        String result = TenderAttachmentUrlResolver.toIntegrationDownloadUrl("doc-insight://TENDER_INTAKE/create-tender/test.docx");
        assertThat(result).contains("doc-insight%3A%2F%2F");
    }

    @Test
    @DisplayName("toIntegrationDownloadUrl CO-280 403 修复：旧 /api/doc-insight/download? URL 重定向到新集成端点")
    void toIntegrationDownloadUrl_legacyDocInsightUrl_redirectsToNewEndpoint() throws Exception {
        setPublicBaseUrl("https://winbid-test.ehsy.com");
        String input = "/api/doc-insight/download?fileUrl=doc-insight%3A%2F%2Ftest.pdf";
        String result = TenderAttachmentUrlResolver.toIntegrationDownloadUrl(input);
        assertThat(result).isEqualTo("https://winbid-test.ehsy.com/api/integration/tenders/attachments/download?fileUrl=doc-insight%3A%2F%2Ftest.pdf");
    }

    @Test
    @DisplayName("toIntegrationDownloadUrl 已是集成下载地址的不再二次包装（幂等）")
    void toIntegrationDownloadUrl_alreadyIntegrationUrl_isIdempotent() throws Exception {
        setPublicBaseUrl("https://winbid-test.ehsy.com");
        String input = "/api/integration/tenders/attachments/download?fileUrl=doc-insight%3A%2F%2Ftest.pdf";
        String result = TenderAttachmentUrlResolver.toIntegrationDownloadUrl(input);
        assertThat(result).isEqualTo("https://winbid-test.ehsy.com/api/integration/tenders/attachments/download?fileUrl=doc-insight%3A%2F%2Ftest.pdf");
    }

    @Test
    @DisplayName("toIntegrationFullUrl 已是集成下载地址且有 apiKey 时追加 api_key")
    void toIntegrationFullUrl_alreadyIntegrationUrlWithApiKey_appendsApiKey() {
        String input = "/api/integration/tenders/attachments/download?fileUrl=doc-insight%3A%2F%2Ftest.pdf";
        String result = TenderAttachmentUrlResolver.toIntegrationFullUrl(input, "xiyu_sk_test_key");
        assertThat(result).isEqualTo("/api/integration/tenders/attachments/download?fileUrl=doc-insight%3A%2F%2Ftest.pdf&api_key=xiyu_sk_test_key");
    }

    @Test
    @DisplayName("toIntegrationDownloadUrl http(s):// 外部 URL 原样返回")
    void toIntegrationDownloadUrl_httpUrl_returnsAsIs() {
        String input = "https://image-c.ehsy.com/files/test.pdf";
        String result = TenderAttachmentUrlResolver.toIntegrationDownloadUrl(input);
        assertThat(result).isEqualTo(input);
    }

    @Test
    @DisplayName("toIntegrationDownloadUrl null 或空字符串原样返回")
    void toIntegrationDownloadUrl_nullOrBlank_returnsAsIs() {
        assertThat(TenderAttachmentUrlResolver.toIntegrationDownloadUrl(null)).isNull();
        assertThat(TenderAttachmentUrlResolver.toIntegrationDownloadUrl("")).isEmpty();
    }

    // ── toIntegrationFullUrl（CRM 集成端点）─────────────────────────────────────

    @Test
    @DisplayName("toIntegrationFullUrl 处理 doc-insight:// 前缀")
    void toIntegrationFullUrl_docInsightUrl_returnsIntegrationDownloadUrl() throws Exception {
        setPublicBaseUrl("https://winbid-test.ehsy.com");
        String result = TenderAttachmentUrlResolver.toIntegrationFullUrl("doc-insight://TENDER_INTAKE/test.pdf");
        assertThat(result).startsWith("https://winbid-test.ehsy.com/api/integration/tenders/attachments/download?fileUrl=");
    }

    @Test
    @DisplayName("toIntegrationFullUrl CO-280 403 修复：旧 /api/doc-insight/download? 重定向到新端点")
    void toIntegrationFullUrl_legacyDocInsightUrl_redirectsToNewEndpoint() throws Exception {
        setPublicBaseUrl("https://winbid-test.ehsy.com");
        String result = TenderAttachmentUrlResolver.toIntegrationFullUrl("/api/doc-insight/download?fileUrl=doc-insight%3A%2F%2Ftest");
        assertThat(result).isEqualTo("https://winbid-test.ehsy.com/api/integration/tenders/attachments/download?fileUrl=doc-insight%3A%2F%2Ftest");
    }

    @Test
    @DisplayName("toIntegrationFullUrl CO-303 修复：完整内部下载 URL 重定向到集成端点并追加 api_key")
    void toIntegrationFullUrl_absoluteLegacyDocInsightUrlWithApiKey_redirectsToNewEndpoint() throws Exception {
        setPublicBaseUrl("https://winbid-test.ehsy.com");
        String input = "https://winbid-test.ehsy.com/api/doc-insight/download?fileUrl=doc-insight%3A%2F%2Ftest.pdf";
        String result = TenderAttachmentUrlResolver.toIntegrationFullUrl(input, "xiyu_sk_test_key");
        assertThat(result).isEqualTo("https://winbid-test.ehsy.com/api/integration/tenders/attachments/download?fileUrl=doc-insight%3A%2F%2Ftest.pdf&api_key=xiyu_sk_test_key");
    }

    @Test
    @DisplayName("toIntegrationFullUrl 处理 http(s):// 完整 URL，原样返回")
    void toIntegrationFullUrl_httpUrl_returnsAsIs() throws Exception {
        setPublicBaseUrl("https://winbid-test.ehsy.com");
        String result = TenderAttachmentUrlResolver.toIntegrationFullUrl("https://image-c.ehsy.com/files/test.pdf");
        assertThat(result).isEqualTo("https://image-c.ehsy.com/files/test.pdf");
    }

    @Test
    @DisplayName("toIntegrationFullUrl 外部 URL 即使包含集成下载路径也不追加 api_key")
    void toIntegrationFullUrl_externalIntegrationLikeUrlWithApiKey_returnsAsIs() throws Exception {
        setPublicBaseUrl("https://winbid-test.ehsy.com");
        String input = "https://external.example/api/integration/tenders/attachments/download?fileUrl=x";
        String result = TenderAttachmentUrlResolver.toIntegrationFullUrl(input, "xiyu_sk_test_key");
        assertThat(result).isEqualTo(input);
    }

    @Test
    @DisplayName("toIntegrationFullUrl 处理 null 值")
    void toIntegrationFullUrl_null_returnsNull() {
        String result = TenderAttachmentUrlResolver.toIntegrationFullUrl(null);
        assertThat(result).isNull();
    }

    // ── normalizeFileUrls（CRM 集成专用，使用新端点）────────────────────────────

    @Test
    @DisplayName("normalizeFileUrls 同时处理 doc-insight:// 和 /api/... 格式，全部指向集成端点")
    void normalizeFileUrls_handlesBothFormats() throws Exception {
        setPublicBaseUrl("https://winbid-test.ehsy.com");
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

        // 使用反射调用 normalizeFileUrls（包私有方法）
        TenderIntegrationMapper mapper = new TenderIntegrationMapper(null, null);
        java.lang.reflect.Method method = TenderIntegrationMapper.class
                .getDeclaredMethod("normalizeFileUrls", TenderDTO.class);
        method.setAccessible(true);
        method.invoke(mapper, dto);

        assertThat(dto.getSourceDocumentFileUrl())
                .startsWith("https://winbid-test.ehsy.com/api/integration/tenders/attachments/download?fileUrl=");
        assertThat(dto.getBidNoticeFileUrl())
                .startsWith("https://winbid-test.ehsy.com/api/integration/tenders/attachments/download?");
        assertThat(dto.getAttachments().get(0).getFileUrl())
                .startsWith("https://winbid-test.ehsy.com/api/integration/tenders/attachments/download?fileUrl=");
    }

    /**
     * 通过反射设置静态字段 publicBaseUrl，模拟 @Value 注入。
     * publicBaseUrl 现在位于 TenderAttachmentUrlResolver（CO-280 403 修复重构）。
     */
    private static void setPublicBaseUrl(String value) throws Exception {
        Field field = TenderAttachmentUrlResolver.class.getDeclaredField("publicBaseUrl");
        field.setAccessible(true);
        field.set(null, value);
    }
}
