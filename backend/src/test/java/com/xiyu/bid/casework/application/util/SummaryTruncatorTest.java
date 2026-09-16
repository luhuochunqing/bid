package com.xiyu.bid.casework.application.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SummaryTruncatorTest {

    // ── null / 空 / 短文本 ──

    @Test
    @DisplayName("null 输入返回空字符串")
    void nullInput() {
        assertThat(SummaryTruncator.truncate(null, 100)).isEmpty();
    }

    @Test
    @DisplayName("空字符串返回空字符串")
    void emptyInput() {
        assertThat(SummaryTruncator.truncate("", 100)).isEmpty();
    }

    @Test
    @DisplayName("短文本（≤maxLength）原样返回，无 ...")
    void shortTextReturnedAsIs() {
        String text = "短文本内容";
        assertThat(SummaryTruncator.truncate(text, 100)).isEqualTo(text);
    }

    @Test
    @DisplayName("恰好等于 maxLength 的文本原样返回")
    void exactLengthTextReturnedAsIs() {
        String text = "a".repeat(100);
        assertThat(SummaryTruncator.truncate(text, 100)).isEqualTo(text);
    }

    // ── 句子边界截断 ──

    @Test
    @DisplayName("纯散文在中文句号后截断")
    void truncateAtChinesePeriod() {
        String text = "第一句话内容。第二句话内容。第三句话内容。第四句话内容。第五句话内容很多很多。";
        String result = SummaryTruncator.truncate(text, 20);
        assertThat(result).endsWith("...");
        assertThat(result).isEqualTo("第一句话内容。第二句话内容。...");
    }

    @Test
    @DisplayName("在中文感叹号后截断")
    void truncateAtChineseExclamation() {
        String text = "第一句话！第二句话！第三句话！第四句话！第五句话内容很多！";
        String result = SummaryTruncator.truncate(text, 12);
        assertThat(result).endsWith("...");
        assertThat(result).isEqualTo("第一句话！第二句话！...");
    }

    @Test
    @DisplayName("在英文问号后截断")
    void truncateAtEnglishQuestionMark() {
        String text = "First question? Second question? Third question is much longer here.";
        String result = SummaryTruncator.truncate(text, 20);
        assertThat(result).endsWith("...");
        assertThat(result).isEqualTo("First question?...");
    }

    // ── 段落 / 换行边界截断 ──

    @Test
    @DisplayName("含 \\n\\n 的文本在段落边界截断（优先于句子边界）")
    void truncateAtParagraphBreak() {
        String text = "第一段内容关于项目概况。\n\n第二段关于技术方案描述，包括微服务架构。";
        String result = SummaryTruncator.truncate(text, 30);
        assertThat(result).endsWith("...");
        assertThat(result).isEqualTo("第一段内容关于项目概况。...");
    }

    @Test
    @DisplayName("含 \\n 的文本在换行边界截断（优先于句子边界）")
    void truncateAtLineBreak() {
        String text = "第一行内容没有句号但是很长的一行文字\n第二行内容也没有句号但是也很长的文字";
        assertThat(text.length()).isGreaterThan(20);
        String result = SummaryTruncator.truncate(text, 20);
        assertThat(result).endsWith("...");
        assertThat(result).isEqualTo("第一行内容没有句号但是很长的一行文字...");
    }

    // ── 序号保护 ──

    @Test
    @DisplayName("阿拉伯数字序号保护：不在 '1.' 和后续内容之间截断")
    void arabicSequenceProtection() {
        String text = "第一句话。1. 项目概述内容说明";
        String result = SummaryTruncator.truncate(text, 10);
        assertThat(result).endsWith("...");
        assertThat(result).isEqualTo("第一句话。...");
    }

    @Test
    @DisplayName("中文序号 '一、' 保护")
    void chineseSequenceProtection() {
        String text = "前言部分。一、项目概况详细说明";
        String result = SummaryTruncator.truncate(text, 10);
        assertThat(result).endsWith("...");
        assertThat(result).isEqualTo("前言部分。...");
    }

    @Test
    @DisplayName("带圈序号 '①' 保护")
    void circledNumberSequenceProtection() {
        String text = "前言部分。① 第一项内容详细说明";
        String result = SummaryTruncator.truncate(text, 10);
        assertThat(result).endsWith("...");
        assertThat(result).isEqualTo("前言部分。...");
    }

    @Test
    @DisplayName("括号序号 '(一)' 保护")
    void parenthesizedChineseSequenceProtection() {
        String text = "前言部分。(一) 第一项内容详细说明很多很多";
        assertThat(text.length()).isGreaterThan(10);
        String result = SummaryTruncator.truncate(text, 10);
        assertThat(result).endsWith("...");
        assertThat(result).isEqualTo("前言部分。...");
    }

    @Test
    @DisplayName("括号序号 '（1）' 保护")
    void parenthesizedArabicSequenceProtection() {
        String text = "前言部分。（1）第一项内容详细说明很多很多";
        assertThat(text.length()).isGreaterThan(10);
        String result = SummaryTruncator.truncate(text, 10);
        assertThat(result).endsWith("...");
        assertThat(result).isEqualTo("前言部分。...");
    }

    @Test
    @DisplayName("多序号项文本截断在完整序号项之后（换行分隔）")
    void multipleSequenceItemsTruncateAtNewline() {
        String text = "1. 项目范围包括XX系统开发\n2. 实施周期12个月\n3. 运维服务3年";
        String result = SummaryTruncator.truncate(text, 30);
        assertThat(result).endsWith("...");
        assertThat(result).isEqualTo("1. 项目范围包括XX系统开发\n2. 实施周期12个月...");
    }

    // ── 兜底硬切 ──

    @Test
    @DisplayName("无标点纯散文兜底硬切")
    void hardCutWhenNoBreakPoint() {
        String text = "这是一段没有任何标点符号的纯散文内容测试用于验证硬切功能是否正确工作";
        String result = SummaryTruncator.truncate(text, 10);
        assertThat(result).isEqualTo("这是一段没有任何标点...");
    }

    // ── maxLength 场景 ──

    @Test
    @DisplayName("maxLength=100 场景（列表卡片）")
    void maxLength100ForCaseCard() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 15; i++) {
            sb.append("这是第").append(i + 1).append("句话的内容描述。");
        }
        String text = sb.toString();
        assertThat(text.length()).isGreaterThan(100);
        String result = SummaryTruncator.truncate(text, 100);
        assertThat(result).endsWith("...");
        assertThat(result).startsWith("这是第1句话的内容描述。");
        String withoutEllipsis = result.substring(0, result.length() - 3);
        assertThat(withoutEllipsis).endsWith("。");
    }

    @Test
    @DisplayName("maxLength=200 场景（推荐卡片）")
    void maxLength200ForRecommendCard() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 30; i++) {
            sb.append("这是第").append(i + 1).append("句话的内容描述信息。");
        }
        String text = sb.toString();
        assertThat(text.length()).isGreaterThan(200);
        String result = SummaryTruncator.truncate(text, 200);
        assertThat(result).endsWith("...");
        String withoutEllipsis = result.substring(0, result.length() - 3);
        assertThat(withoutEllipsis).endsWith("。");
    }

    // ── 截断结果格式 ──

    @Test
    @DisplayName("截断结果以 '...' 结尾")
    void truncatedResultEndsWithEllipsis() {
        String text = "第一句话。第二句话。第三句话。第四句话。第五句话。第六句话内容很多。";
        String result = SummaryTruncator.truncate(text, 15);
        assertThat(result).endsWith("...");
    }

    @Test
    @DisplayName("未截断文本不以 '...' 结尾")
    void nonTruncatedTextHasNoEllipsis() {
        String text = "短文本";
        String result = SummaryTruncator.truncate(text, 100);
        assertThat(result).doesNotEndWith("...");
    }

    // ── 边界补充 ──

    @Test
    @DisplayName("\\r\\n\\r\\n 段落边界识别")
    void carriageReturnParagraphBreak() {
        String text = "第一段内容关于项目概况说明。\r\n\r\n第二段关于技术方案的详细描述。";
        String result = SummaryTruncator.truncate(text, 25);
        assertThat(result).endsWith("...");
        assertThat(result).isEqualTo("第一段内容关于项目概况说明。...");
    }

    @Test
    @DisplayName("序号后紧跟内容（有空格）时正常截断在句子边界")
    void sequenceWithContentTruncatesAtSentence() {
        String text = "1. 项目概述内容说明。2. 技术方案描述。3. 运维服务内容。";
        String result = SummaryTruncator.truncate(text, 25);
        assertThat(result).endsWith("...");
        assertThat(result).isEqualTo("1. 项目概述内容说明。2. 技术方案描述。...");
    }

    @Test
    @DisplayName("无断点硬切在 maxLength 边界保护阿拉伯序号")
    void hardCutProtectsArabicMarkerAtBoundary() {
        String text = "a".repeat(99) + "1. 项目";
        assertThat(SummaryTruncator.truncate(text, 100)).isEqualTo("a".repeat(99) + "...");
    }

    @Test
    @DisplayName("无断点硬切在 maxLength 边界保护中文、带圈和括号序号")
    void hardCutProtectsOtherMarkersAtBoundary() {
        assertThat(SummaryTruncator.truncate("a".repeat(99) + "一、项目", 100)).isEqualTo("a".repeat(99) + "...");
        assertThat(SummaryTruncator.truncate("a".repeat(99) + "①项目", 100)).isEqualTo("a".repeat(99) + "...");
        assertThat(SummaryTruncator.truncate("a".repeat(98) + "（1）项目", 100)).isEqualTo("a".repeat(98) + "...");
    }
}
