package com.xiyu.bid.integration.external;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link TenderIntegrationMapper#toDownloadUrl} 和 {@link TenderIntegrationMapper#toFullUrl} 的单元测试。
 * 覆盖场景：
 * 1. 未配置 publicBaseUrl（开发环境同源）→ 返回相对路径
 * 2. 配置了 publicBaseUrl（生产环境供 CRM 跨域）→ 返回完整 URL
 * 3. toFullUrl 处理 doc-insight://、/api/...、null 及其他 URL
 */
class TenderIntegrationMapperToDownloadUrlTest {

    @AfterEach
    void tearDown() {
        // 清理 static 字段，避免影响其他测试
        ReflectionTestUtils.setField(TenderIntegrationMapper.class, "publicBaseUrl", null);
    }

    @Test
    @DisplayName("toDownloadUrl: publicBaseUrl 为空 → 返回相对路径（开发环境同源）")
    void toDownloadUrl_emptyBaseUrl_returnsRelativePath() {
        ReflectionTestUtils.setField(TenderIntegrationMapper.class, "publicBaseUrl", "");

        String url = TenderIntegrationMapper.toDownloadUrl("doc-insight://TENDER_INTAKE/create-tender/test.pdf");

        String expected = "/api/doc-insight/download?fileUrl=" +
                URLEncoder.encode("doc-insight://TENDER_INTAKE/create-tender/test.pdf", StandardCharsets.UTF_8);
        assertThat(url).isEqualTo(expected);
    }

    @Test
    @DisplayName("toDownloadUrl: publicBaseUrl 为 null → 返回相对路径（兼容未配置场景）")
    void toDownloadUrl_nullBaseUrl_returnsRelativePath() {
        ReflectionTestUtils.setField(TenderIntegrationMapper.class, "publicBaseUrl", null);

        String url = TenderIntegrationMapper.toDownloadUrl("doc-insight://TENDER_INTAKE/create-tender/test.pdf");

        String expected = "/api/doc-insight/download?fileUrl=" +
                URLEncoder.encode("doc-insight://TENDER_INTAKE/create-tender/test.pdf", StandardCharsets.UTF_8);
        assertThat(url).isEqualTo(expected);
    }

    @Test
    @DisplayName("toDownloadUrl: publicBaseUrl 配置 → 返回完整 URL（生产环境供 CRM 跨域）")
    void toDownloadUrl_withBaseUrl_returnsFullUrl() {
        ReflectionTestUtils.setField(TenderIntegrationMapper.class, "publicBaseUrl", "http://172.16.38.78:8080");

        String url = TenderIntegrationMapper.toDownloadUrl("doc-insight://TENDER_INTAKE/create-tender/test.pdf");

        String expected = "http://172.16.38.78:8080/api/doc-insight/download?fileUrl=" +
                URLEncoder.encode("doc-insight://TENDER_INTAKE/create-tender/test.pdf", StandardCharsets.UTF_8);
        assertThat(url).isEqualTo(expected);
    }

    @Test
    @DisplayName("toDownloadUrl: publicBaseUrl 配置且 fileUrl 含中文 → 完整 URL 且中文被正确编码")
    void toDownloadUrl_withBaseUrlAndChineseFileUrl_returnsFullUrlWithEncodedChinese() {
        ReflectionTestUtils.setField(TenderIntegrationMapper.class, "publicBaseUrl", "http://172.16.38.78:8080");

        String url = TenderIntegrationMapper.toDownloadUrl("doc-insight://TENDER_INTAKE/create-tender/b71ae406e641-标讯文件示例.docx");

        String expected = "http://172.16.38.78:8080/api/doc-insight/download?fileUrl=" +
                URLEncoder.encode("doc-insight://TENDER_INTAKE/create-tender/b71ae406e641-标讯文件示例.docx", StandardCharsets.UTF_8);
        assertThat(url).isEqualTo(expected);
        assertThat(url).contains("%E6%A0%87%E8%AE%AF%E6%96%87%E4%BB%B6");
    }

    @Test
    @DisplayName("toFullUrl: null → null")
    void toFullUrl_null_returnsNull() {
        ReflectionTestUtils.setField(TenderIntegrationMapper.class, "publicBaseUrl", "http://172.16.38.78:8080");
        assertThat(TenderIntegrationMapper.toFullUrl(null)).isNull();
    }

    @Test
    @DisplayName("toFullUrl: doc-insight:// URL → 走 toDownloadUrl 转换为完整 URL")
    void toFullUrl_docInsightUrl_convertsToFullUrl() {
        ReflectionTestUtils.setField(TenderIntegrationMapper.class, "publicBaseUrl", "http://172.16.38.78:8080");

        String url = TenderIntegrationMapper.toFullUrl("doc-insight://TENDER_INTAKE/create-tender/test.pdf");

        String expected = "http://172.16.38.78:8080/api/doc-insight/download?fileUrl=" +
                URLEncoder.encode("doc-insight://TENDER_INTAKE/create-tender/test.pdf", StandardCharsets.UTF_8);
        assertThat(url).isEqualTo(expected);
    }

    @Test
    @DisplayName("toFullUrl: /api/... 相对路径 + publicBaseUrl 配置 → 补全为完整 URL")
    void toFullUrl_relativeApiPath_withBaseUrl_prependsBaseUrl() {
        ReflectionTestUtils.setField(TenderIntegrationMapper.class, "publicBaseUrl", "http://172.16.38.78:8080");

        String url = TenderIntegrationMapper.toFullUrl("/api/doc-insight/download?fileUrl=test.pdf");

        assertThat(url).isEqualTo("http://172.16.38.78:8080/api/doc-insight/download?fileUrl=test.pdf");
    }

    @Test
    @DisplayName("toFullUrl: /api/... 相对路径 + publicBaseUrl 为空 → 保持相对路径")
    void toFullUrl_relativeApiPath_emptyBaseUrl_keepsRelative() {
        ReflectionTestUtils.setField(TenderIntegrationMapper.class, "publicBaseUrl", "");

        String url = TenderIntegrationMapper.toFullUrl("/api/doc-insight/download?fileUrl=test.pdf");

        assertThat(url).isEqualTo("/api/doc-insight/download?fileUrl=test.pdf");
    }

    @Test
    @DisplayName("toFullUrl: 其他格式 URL（如 http://...）→ 原样返回")
    void toFullUrl_otherUrl_returnsAsIs() {
        ReflectionTestUtils.setField(TenderIntegrationMapper.class, "publicBaseUrl", "http://172.16.38.78:8080");

        String url = TenderIntegrationMapper.toFullUrl("https://example.com/file.pdf");

        assertThat(url).isEqualTo("https://example.com/file.pdf");
    }
}
