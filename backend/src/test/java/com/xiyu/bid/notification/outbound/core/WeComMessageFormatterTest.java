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

    // ============ 企微文案丢失修复：body 字段优先级 ============

    @Test
    void format_WithNonBlankBody_UsesBodyAsDescription() {
        // body 非空时，description 直接使用 body（展示完整通知正文，如 CA 预警的关联平台/CA类型）
        String body = "【CA已过期】某公司（关联平台：xx平台，CA类型：实体CA）已于 2026-07-22 过期，请立即处理";
        WeComMessageFormatter.FormattedMessage message = WeComMessageFormatter.format(
            "CA证书到期提醒", body, "CA_EXPIRED", "CA_CERTIFICATE", 100L,
            "https://xiyu.example.com", "/resource/ca-management"
        );

        assertThat(message.description()).isEqualTo(body);
        // 不应包含 type label 拼接（"通知 · ..."）
        assertThat(message.description()).doesNotContain("·");
    }

    @Test
    void format_WithNullBody_FallsBackToTypeAndTitleDescription() {
        // body 为 null 时，回退到老版本行为：typeLabel + " · " + title
        WeComMessageFormatter.FormattedMessage message = WeComMessageFormatter.format(
            "标书评审通过", null, "APPROVAL", "PROJECT", 42L,
            "https://xiyu.example.com", null
        );

        assertThat(message.description()).contains("审批");
        assertThat(message.description()).contains("标书评审通过");
        assertThat(message.description()).contains("·");
    }

    @Test
    void format_WithBlankBody_FallsBackToTypeAndTitleDescription() {
        // body 为空白字符串时也应回退，避免企微消息出现空白 description
        WeComMessageFormatter.FormattedMessage message = WeComMessageFormatter.format(
            "标书评审通过", "   ", "APPROVAL", "PROJECT", 42L,
            "https://xiyu.example.com", null
        );

        assertThat(message.description()).contains("审批");
        assertThat(message.description()).contains("标书评审通过");
    }

    @Test
    void format_WithOversizedBody_TruncatesTo512() {
        // body 超长时截断，避免企微 textcard description 字段超限
        String longBody = "X".repeat(600);
        WeComMessageFormatter.FormattedMessage message = WeComMessageFormatter.format(
            "通知", longBody, "INFO", null, null, "https://xiyu.example.com", null
        );

        assertThat(message.description()).hasSize(512);
    }

    @Test
    void format_WithBody_PreservesUrlAndTitle() {
        // 有 body 时，title 和 url 仍按原规则解析，body 只影响 description
        String body = "CA 即将到期提醒正文";
        WeComMessageFormatter.FormattedMessage message = WeComMessageFormatter.format(
            "CA证书到期提醒", body, "CA_EXPIRING", "CA_CERTIFICATE", 100L,
            "https://xiyu.example.com", "/resource/ca-management"
        );

        assertThat(message.title()).isEqualTo("CA证书到期提醒");
        assertThat(message.url()).isEqualTo("https://xiyu.example.com/resource/ca-management");
        assertThat(message.description()).isEqualTo(body);
    }
}
