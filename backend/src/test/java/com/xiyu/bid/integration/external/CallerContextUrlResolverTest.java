package com.xiyu.bid.integration.external;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CO-280 根因治理测试：验证 CallerContext + TenderAttachmentUrlResolver 的统一入口
 * 按"内部用户 vs 外部系统"自动选择端点和认证方式。
 */
class CallerContextUrlResolverTest {

    private static final String DOC_INSIGHT = "doc-insight://STORAGE/sample.pdf";

    // ── CallerContext 基础语义 ─────────────────────────────────────────────

    @Test
    void internalUserContext_hasCorrectTypeAndNoKey() {
        CallerContext ctx = CallerContext.internalUser();
        assertTrue(ctx.isInternalUser());
        assertTrue(!ctx.isExternalSystem());
        assertNull(ctx.apiKey());
        assertEquals(CallerContext.CallerType.INTERNAL_USER, ctx.type());
    }

    @Test
    void externalSystemContext_hasCorrectTypeAndKey() {
        CallerContext ctx = CallerContext.externalSystem("xiyu_sk_test_key");
        assertTrue(ctx.isExternalSystem());
        assertTrue(!ctx.isInternalUser());
        assertEquals("xiyu_sk_test_key", ctx.apiKey());
        assertEquals(CallerContext.CallerType.EXTERNAL_SYSTEM, ctx.type());
    }

    @Test
    void externalSystemContext_nullKey_degradesGracefully() {
        CallerContext ctx = CallerContext.externalSystem(null);
        assertTrue(ctx.isExternalSystem());
        assertNull(ctx.apiKey());
    }

    // ── resolve() 按上下文选择正确端点 ───────────────────────────────

    @Test
    void resolve_internalUser_prefersInternalDownloadEndpoint() {
        CallerContext ctx = CallerContext.internalUser();
        String url = TenderAttachmentUrlResolver.resolve(DOC_INSIGHT, ctx);
        assertNotNull(url);
        // 内部用户应使用 /api/doc-insight/download 端点
        assertTrue(url.contains("/api/doc-insight/download"),
                "Internal user URL should contain internal endpoint, got: " + url);
        // 内部端点不附加 api_key
        assertTrue(!url.contains("api_key="),
                "Internal user URL should not contain api_key, got: " + url);
    }

    @Test
    void resolve_externalSystemWithApiKey_usesIntegrationEndpointWithParam() {
        CallerContext ctx = CallerContext.externalSystem("xiyu_sk_test_key");
        String url = TenderAttachmentUrlResolver.resolve(DOC_INSIGHT, ctx);
        assertNotNull(url);
        // 外部系统应使用 /api/integration/tenders/attachments/download 端点
        assertTrue(url.contains("/api/integration/tenders/attachments/download"),
                "External system URL should contain integration endpoint, got: " + url);
        // URL 中包含 api_key 参数，使浏览器点击即下载
        assertTrue(url.contains("api_key=xiyu_sk_test_key"),
                "External system URL should contain api_key parameter, got: " + url);
    }

    @Test
    void resolve_externalSystemWithoutApiKey_usesIntegrationEndpoint() {
        CallerContext ctx = CallerContext.externalSystem(null);
        String url = TenderAttachmentUrlResolver.resolve(DOC_INSIGHT, ctx);
        assertNotNull(url);
        // 无 API Key 时仍使用集成端点，但不加 api_key 参数
        assertTrue(url.contains("/api/integration/tenders/attachments/download"),
                "External system URL (no key) should contain integration endpoint, got: " + url);
        assertTrue(!url.contains("api_key="),
                "External system URL (no key) should NOT contain api_key, got: " + url);
    }

    @Test
    void resolve_nullUrl_returnsNull_safe() {
        assertNull(TenderAttachmentUrlResolver.resolve(null, CallerContext.internalUser()));
        assertNull(TenderAttachmentUrlResolver.resolve(null, CallerContext.externalSystem("x")));
    }

    @Test
    void resolve_emptyUrl_returnsEmpty_safe() {
        String empty = "";
        assertEquals(empty, TenderAttachmentUrlResolver.resolve(empty, CallerContext.internalUser()));
        assertEquals(empty, TenderAttachmentUrlResolver.resolve(empty, CallerContext.externalSystem("x")));
    }

    // ── resolveBatch() 批量转换 ─────────────────────────────────────

    @Test
    void resolveBatch_internalUser_allUrlsStandardized() {
        List<String> inputs = Arrays.asList(
                DOC_INSIGHT,
                null,
                "",
                "https://example.com/already-external.pdf"
        );
        CallerContext ctx = CallerContext.internalUser();
        List<String> results = TenderAttachmentUrlResolver.resolveBatch(inputs, ctx);

        assertEquals(inputs.size(), results.size(), "Batch result size must match input");
        // 第一个 doc-insight 应转换为内部端点
        assertTrue(results.get(0).contains("/api/doc-insight/download"),
                "doc-insight URL should be converted, got: " + results.get(0));
        // null 和空值保持原样
        assertNull(results.get(1));
        assertEquals("", results.get(2));
        // http(s) URL 原样返回
        assertEquals("https://example.com/already-external.pdf", results.get(3));
    }

    @Test
    void resolveBatch_externalSystemWithApiKey_allUrlsContainApiKeyParam() {
        List<String> inputs = Arrays.asList(
                DOC_INSIGHT,
                "doc-insight://OTHER/file.txt",
                null
        );
        CallerContext ctx = CallerContext.externalSystem("xiyu_sk_test_key");
        List<String> results = TenderAttachmentUrlResolver.resolveBatch(inputs, ctx);

        assertEquals(inputs.size(), results.size());
        // doc-insight:// URL 应转换为集成端点，并附加 api_key
        assertTrue(results.get(0).contains("/api/integration/tenders/attachments/download"),
                "First URL should use integration endpoint, got: " + results.get(0));
        assertTrue(results.get(0).contains("api_key=xiyu_sk_test_key"),
                "First URL should contain api_key, got: " + results.get(0));
        assertNull(results.get(2));
    }

    // ── 与旧 API 兼容：resolve() 的输出应等价于直接调用底层方法 ─

    @Test
    void resolve_internalUser_equivalentTo_toDownloadUrl() {
        CallerContext ctx = CallerContext.internalUser();
        String viaResolve = TenderAttachmentUrlResolver.resolve(DOC_INSIGHT, ctx);
        String direct = TenderAttachmentUrlResolver.toDownloadUrl(DOC_INSIGHT);
        assertEquals(direct, viaResolve);
    }

    @Test
    void resolve_externalSystemWithKey_equivalentTo_toIntegrationFullUrlWithKey() {
        String key = "xiyu_sk_test_key";
        CallerContext ctx = CallerContext.externalSystem(key);
        String viaResolve = TenderAttachmentUrlResolver.resolve(DOC_INSIGHT, ctx);
        String direct = TenderAttachmentUrlResolver.toIntegrationFullUrl(DOC_INSIGHT, key);
        assertEquals(direct, viaResolve);
    }

    @Test
    void resolve_externalSystemNoKey_equivalentTo_toIntegrationFullUrlNoParam() {
        CallerContext ctx = CallerContext.externalSystem(null);
        String viaResolve = TenderAttachmentUrlResolver.resolve(DOC_INSIGHT, ctx);
        String direct = TenderAttachmentUrlResolver.toIntegrationFullUrl(DOC_INSIGHT);
        assertEquals(direct, viaResolve);
    }
}
