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
}
