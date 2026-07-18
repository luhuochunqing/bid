package com.xiyu.bid.notification.outbound.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("WeComMessageFormatter — pure formatting for 企微 textcard payload")
class WeComMessageFormatterTest {

    @Test
    void format_WithProjectSource_BuildsProjectUrl() {
        WeComMessageFormatter.FormattedMessage message = WeComMessageFormatter.format(
            "标书评审通过", "APPROVAL", "PROJECT", 42L, "https://xiyu.example.com"
        );

        assertThat(message.title()).isEqualTo("标书评审通过");
        assertThat(message.description()).contains("审批");
        assertThat(message.description()).contains("标书评审通过");
        assertThat(message.url()).isEqualTo("https://xiyu.example.com/project/42");
        assertThat(message.btnText()).isEqualTo("查看详情");
    }

    @Test
    void format_WithDocumentSource_BuildsDocumentUrl() {
        WeComMessageFormatter.FormattedMessage message = WeComMessageFormatter.format(
            "文档已更新", "DOCUMENT_CHANGE", "DOCUMENT", 7L, "https://xiyu.example.com/"
        );

        assertThat(message.url()).isEqualTo("https://xiyu.example.com/document/editor/7");
    }

    @Test
    void format_WithoutSource_FallsBackToInbox() {
        WeComMessageFormatter.FormattedMessage message = WeComMessageFormatter.format(
            "系统通知", "SYSTEM", null, null, "https://xiyu.example.com"
        );

        assertThat(message.url()).isEqualTo("https://xiyu.example.com/inbox");
    }

    @Test
    void format_WithUnknownSourceType_FallsBackToInbox() {
        WeComMessageFormatter.FormattedMessage message = WeComMessageFormatter.format(
            "提醒", "INFO", "UNKNOWN_TYPE", 1L, "https://xiyu.example.com"
        );

        assertThat(message.url()).isEqualTo("https://xiyu.example.com/inbox");
    }

    @Test
    void format_WithBlankTitle_UsesFallback() {
        WeComMessageFormatter.FormattedMessage message = WeComMessageFormatter.format(
            "", "MENTION", "PROJECT", 1L, "https://xiyu.example.com"
        );

        assertThat(message.title()).isEqualTo("新通知");
    }

    @Test
    void format_WithOversizedTitle_TruncatesTo128() {
        String longTitle = "X".repeat(200);
        WeComMessageFormatter.FormattedMessage message = WeComMessageFormatter.format(
            longTitle, "INFO", null, null, "https://xiyu.example.com"
        );

        assertThat(message.title()).hasSize(128);
    }

    @Test
    void format_WithPendingInitiationType_UsesPendingInitiationLabel() {
        WeComMessageFormatter.FormattedMessage message = WeComMessageFormatter.format(
            "待立项通知", "PENDING_INITIATION", "PROJECT", 1L, "https://xiyu.example.com"
        );

        assertThat(message.description()).contains("待立项");
    }

    @Test
    void format_WithPendingClosureApplicationType_UsesPendingClosureLabel() {
        WeComMessageFormatter.FormattedMessage message = WeComMessageFormatter.format(
            "待结项申请通知", "PENDING_CLOSURE_APPLICATION", "PROJECT", 1L, "https://xiyu.example.com"
        );

        assertThat(message.description()).contains("待结项");
    }

    @Test
    void format_WithUppercaseBidding_BuildsBiddingUrl() {
        WeComMessageFormatter.FormattedMessage message = WeComMessageFormatter.format(
            "投标项目更新", "TASK_UPDATE", "BIDDING", 9L, "https://xiyu.example.com"
        );

        assertThat(message.url()).isEqualTo("https://xiyu.example.com/bidding/9");
    }

    @Test
    void format_WithLowercaseEntityType_StillResolves() {
        WeComMessageFormatter.FormattedMessage message = WeComMessageFormatter.format(
            "新项目", "INFO", "project", 5L, "https://xiyu.example.com"
        );

        assertThat(message.url()).isEqualTo("https://xiyu.example.com/project/5");
    }

    @Test
    void format_WithNegativeEntityId_FallsBackToInbox() {
        WeComMessageFormatter.FormattedMessage message = WeComMessageFormatter.format(
            "test", "INFO", "PROJECT", -1L, "https://xiyu.example.com"
        );

        assertThat(message.url()).endsWith("/inbox");
    }

    // ============ P0-1：payload targetUrl 覆盖默认 entityType 映射 ============

    @Test
    void format_WithPayloadTargetUrl_OverridesEntityTypeMapping() {
        // 文档变更通知：sourceEntityType=DOCUMENT 但合理跳转目标是项目 drafting 页
        WeComMessageFormatter.FormattedMessage message = WeComMessageFormatter.format(
            "文档变更 - 测试项目", "DOCUMENT_CHANGE", "DOCUMENT", 7L,
            "https://xiyu.example.com", "/project/100/drafting"
        );

        // 关键断言：使用 payload targetUrl，而非 /document/editor/7
        assertThat(message.url()).isEqualTo("https://xiyu.example.com/project/100/drafting");
    }

    @Test
    void format_WithNullPayloadTargetUrl_FallsBackToEntityTypeMapping() {
        // 向后兼容：未透传 targetUrl 时走 entityType 映射
        WeComMessageFormatter.FormattedMessage message = WeComMessageFormatter.format(
            "文档已更新", "DOCUMENT_CHANGE", "DOCUMENT", 7L, "https://xiyu.example.com", null
        );

        assertThat(message.url()).isEqualTo("https://xiyu.example.com/document/editor/7");
    }

    @Test
    void format_WithRelativePathTargetUrl_AcceptedAsOverride() {
        WeComMessageFormatter.FormattedMessage message = WeComMessageFormatter.format(
            "通知", "INFO", "PROJECT", 1L, "https://xiyu.example.com", "/custom/path/123"
        );

        assertThat(message.url()).isEqualTo("https://xiyu.example.com/custom/path/123");
    }

    @Test
    void format_WithNonPathPayloadTargetUrl_IgnoredFallsBackToMapping() {
        // 防御：targetUrl 不以 "/" 开头（如 "https://evil.com"）时不采用，避免开放重定向
        WeComMessageFormatter.FormattedMessage message = WeComMessageFormatter.format(
            "通知", "INFO", "PROJECT", 1L, "https://xiyu.example.com", "https://evil.com/path"
        );

        assertThat(message.url()).isEqualTo("https://xiyu.example.com/project/1");
    }

    @Test
    void format_WithBlankPayloadTargetUrl_FallsBackToMapping() {
        WeComMessageFormatter.FormattedMessage message = WeComMessageFormatter.format(
            "通知", "INFO", "PROJECT", 1L, "https://xiyu.example.com", ""
        );

        assertThat(message.url()).isEqualTo("https://xiyu.example.com/project/1");
    }

    @Test
    void format_legacy5ArgOverload_StillWorksAfterBackwardCompatAdded() {
        // 5 参数重载（无 targetUrl）必须保持原行为，老调用方无破坏
        WeComMessageFormatter.FormattedMessage message = WeComMessageFormatter.format(
            "通知", "INFO", "PROJECT", 1L, "https://xiyu.example.com"
        );

        assertThat(message.url()).isEqualTo("https://xiyu.example.com/project/1");
    }

    // ============ SSO 启用场景：URL 构造为 OAuth 授权链接 ============

    @Test
    void format_WithSsoParams_BuildsOAuthAuthorizeUrl() {
        WeComMessageFormatter.WeComSsoParams ssoParams =
            new WeComMessageFormatter.WeComSsoParams("wx045d055c4e7bab5e", "1000322");

        WeComMessageFormatter.FormattedMessage message = WeComMessageFormatter.format(
            "标书评审通过", "APPROVAL", "PROJECT", 42L,
            "https://winbid-test.ehsy.com", null, ssoParams
        );

        String url = message.url();
        // 关键断言：URL 是 OAuth 授权链接
        assertThat(url).startsWith("https://open.weixin.qq.com/connect/oauth2/authorize?");
        assertThat(url).contains("appid=wx045d055c4e7bab5e");
        assertThat(url).contains("agentid=1000322");
        assertThat(url).contains("response_type=code");
        assertThat(url).contains("scope=snsapi_base");
        assertThat(url).contains("state=msg");
        assertThat(url).endsWith("#wechat_redirect");
        // redirect_uri 应编码 login?redirect=/project/42
        assertThat(url).contains("redirect_uri=");
        // 不应包含直接业务 URL
        assertThat(url).doesNotContain("winbid-test.ehsy.com/project/42");
    }

    @Test
    void format_WithSsoParamsAndPayloadTargetUrl_BuildsOAuthUrlWithCustomRedirect() {
        WeComMessageFormatter.WeComSsoParams ssoParams =
            new WeComMessageFormatter.WeComSsoParams("wx045d055c4e7bab5e", "1000322");

        WeComMessageFormatter.FormattedMessage message = WeComMessageFormatter.format(
            "文档变更", "DOCUMENT_CHANGE", "DOCUMENT", 7L,
            "https://winbid-test.ehsy.com", "/project/100/drafting", ssoParams
        );

        String url = message.url();
        assertThat(url).startsWith("https://open.weixin.qq.com/connect/oauth2/authorize?");
        // redirect_uri 应编码 login?redirect=/project/100/drafting（而非 /document/editor/7）
        assertThat(url).doesNotContain("document%2Feditor%2F7");
    }

    @Test
    void format_WithNullSsoParams_FallsBackToDirectBusinessUrl() {
        // 向后兼容：ssoParams=null 时返回直接业务 URL
        WeComMessageFormatter.FormattedMessage message = WeComMessageFormatter.format(
            "通知", "INFO", "PROJECT", 1L, "https://xiyu.example.com", null, null
        );

        assertThat(message.url()).isEqualTo("https://xiyu.example.com/project/1");
    }

    @Test
    void format_WithInvalidSsoParams_FallsBackToDirectBusinessUrl() {
        // corpId 为空 → 无效 → 返回直接业务 URL
        WeComMessageFormatter.WeComSsoParams invalid =
            new WeComMessageFormatter.WeComSsoParams("", "1000322");

        WeComMessageFormatter.FormattedMessage message = WeComMessageFormatter.format(
            "通知", "INFO", "PROJECT", 1L, "https://xiyu.example.com", null, invalid
        );

        assertThat(message.url()).isEqualTo("https://xiyu.example.com/project/1");
    }

    @Test
    void format_WithSsoParams_InboxFallback_BuildsOAuthUrlWithInboxRedirect() {
        // sourceEntityType=null → 回退到 /inbox，但 SSO 仍应构造 OAuth URL
        WeComMessageFormatter.WeComSsoParams ssoParams =
            new WeComMessageFormatter.WeComSsoParams("wx045d055c4e7bab5e", "1000322");

        WeComMessageFormatter.FormattedMessage message = WeComMessageFormatter.format(
            "系统通知", "SYSTEM", null, null,
            "https://winbid-test.ehsy.com", null, ssoParams
        );

        String url = message.url();
        assertThat(url).startsWith("https://open.weixin.qq.com/connect/oauth2/authorize?");
        // redirect_uri 应编码 login?redirect=/inbox
        assertThat(url).doesNotContain("winbid-test.ehsy.com/inbox");
    }

    @Test
    void oauthStateFromMessageConstant_IsPublicAccessible() {
        // 验证常量值（WeComAuthController 依赖此常量识别消息推送来源的 state）
        assertThat(WeComMessageFormatter.OAUTH_STATE_FROM_MESSAGE).isEqualTo("msg");
    }
}
