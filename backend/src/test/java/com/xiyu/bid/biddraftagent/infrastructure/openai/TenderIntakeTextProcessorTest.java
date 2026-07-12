// Input: 各种文本输入和 structuredMetadata JSON
// Output: 断言 buildRegexHints / parseSectionsFromMetadata / normalizeTime 的行为
// Pos: biddraftagent/infrastructure/openai — 正则预提取 & sections 解析单元测试
package com.xiyu.bid.biddraftagent.infrastructure.openai;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.DisplayName;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TenderIntakeTextProcessor")
class TenderIntakeTextProcessorTest {

    // ── normalizeTime ──────────────────────────────────────────────

    @Nested
    @DisplayName("normalizeTime")
    class NormalizeTime {

        @Test
        @DisplayName("HH:mm → HH:mm:00")
        void shouldPadSecondsWhenOnlyHourMinute() {
            assertThat(TenderIntakeTextProcessor.normalizeTime("09:30"))
                    .isEqualTo("09:30:00");
        }

        @Test
        @DisplayName("HH:mm:ss → 保持不变")
        void shouldPreserveWhenSecondsAlreadyPresent() {
            assertThat(TenderIntakeTextProcessor.normalizeTime("14:30:00"))
                    .isEqualTo("14:30:00");
        }

        @Test
        @DisplayName("null / blank → 空字符串")
        void shouldReturnEmptyForNullOrBlank() {
            assertThat(TenderIntakeTextProcessor.normalizeTime(null)).isEqualTo("");
            assertThat(TenderIntakeTextProcessor.normalizeTime("")).isEqualTo("");
            assertThat(TenderIntakeTextProcessor.normalizeTime("  ")).isEqualTo("");
        }
    }

    // ── buildRegexHints ────────────────────────────────────────────

    @Nested
    @DisplayName("buildRegexHints")
    class BuildRegexHints {

        @Test
        @DisplayName("null / 空文本 → （无预提取信息）")
        void shouldReturnEmptyHintForNullOrBlank() {
            assertThat(TenderIntakeTextProcessor.buildRegexHints(null))
                    .isEqualTo("（无预提取信息）");
            assertThat(TenderIntakeTextProcessor.buildRegexHints(""))
                    .isEqualTo("（无预提取信息）");
            assertThat(TenderIntakeTextProcessor.buildRegexHints("   "))
                    .isEqualTo("（无预提取信息）");
        }

        @Test
        @DisplayName("无匹配 → （无预提取信息）")
        void shouldReturnEmptyHintForNoMatches() {
            assertThat(TenderIntakeTextProcessor.buildRegexHints("这是一段没有日期和金额的纯文本"))
                    .isEqualTo("（无预提取信息）");
        }

        @Test
        @DisplayName("提取日期（含上下文标签）")
        void shouldExtractDatesWithContextLabels() {
            String text = "获取文件时间：2026年7月01日至2026年7月07日\n投标截止时间：2026年7月21日 09:30";
            String hints = TenderIntakeTextProcessor.buildRegexHints(text);

            assertThat(hints).contains("2026-07-01");
            assertThat(hints).contains("2026-07-07");
            assertThat(hints).contains("2026-07-21T09:30:00");
            assertThat(hints).contains("上下文：");
            assertThat(hints).contains("获取文件时间：");
            assertThat(hints).contains("投标截止时间：");
        }

        @Test
        @DisplayName("提取日期（HH:mm:ss 格式不被重复拼 :00）")
        void shouldNotDoublePadSeconds() {
            String text = "开标时间：2026年8月15日 14:30:00";
            String hints = TenderIntakeTextProcessor.buildRegexHints(text);

            assertThat(hints).contains("2026-08-15T14:30:00");
            assertThat(hints).doesNotContain("14:30:00:00");
        }

        @Test
        @DisplayName("提取金额（含归一化）")
        void shouldExtractAmountsWithNormalization() {
            String text = "预算金额：500万元\n合同金额：6800000元";
            String hints = TenderIntakeTextProcessor.buildRegexHints(text);

            assertThat(hints).contains("500万元（归一化：5000000元）");
            assertThat(hints).contains("6800000元");
        }

        @Test
        @DisplayName("提取手机号（边界匹配，不误匹配长数字串中的片段）")
        void shouldExtractPhonesWithWordBoundaries() {
            String text = "联系人：13800138000，订单号：2026072113800138000123";
            String hints = TenderIntakeTextProcessor.buildRegexHints(text);

            assertThat(hints).contains("13800138000");
            // 订单号中的长数字串不应匹配出手机号片段
            assertThat(hints).doesNotContain("13800138000、13800138000");
        }

        @Test
        @DisplayName("提取邮箱")
        void shouldExtractEmails() {
            String text = "联系邮箱：test@example.com";
            String hints = TenderIntakeTextProcessor.buildRegexHints(text);

            assertThat(hints).contains("test@example.com");
        }
    }

    // ── buildTenderIntakeCandidateText ─────────────────────────────

    @Nested
    @DisplayName("buildTenderIntakeCandidateText")
    class BuildTenderIntakeCandidateText {

        @Test
        @DisplayName("null / 空字符串 → 空字符串")
        void shouldReturnEmptyForNullOrEmpty() {
            assertThat(TenderIntakeTextProcessor.buildTenderIntakeCandidateText(null)).isEmpty();
            assertThat(TenderIntakeTextProcessor.buildTenderIntakeCandidateText("")).isEmpty();
        }

        @Test
        @DisplayName("纯空白无关键词 → 回退到原空白前 8000 字符")
        void shouldFallbackToWhitespaceWhenNoKeyword() {
            String whitespace = "   \n\t  ";
            String result = TenderIntakeTextProcessor.buildTenderIntakeCandidateText(whitespace);
            assertThat(result).isEqualTo(whitespace);
        }

        @Test
        @DisplayName("无关键词时回退到前 8000 字符")
        void shouldFallbackToFirst8000CharsWhenNoKeyword() {
            String repeated = "这是一段没有任何招标关键词的填充文本。".repeat(300);

            String result = TenderIntakeTextProcessor.buildTenderIntakeCandidateText(repeated);

            assertThat(result).isEqualTo(repeated.substring(0, Math.min(repeated.length(), 8_000)));
        }

        @Test
        @DisplayName("匹配关键词时保留上下文半径（前后各 3 行）")
        void shouldIncludeContextRadiusAroundKeywordLines() {
            String text = String.join("\n", List.of(
                    "第1行",
                    "第2行",
                    "第3行",
                    "第4行",
                    "项目预算：100万元",
                    "第6行",
                    "第7行",
                    "第8行",
                    "第9行"));

            String result = TenderIntakeTextProcessor.buildTenderIntakeCandidateText(text);

            assertThat(result).contains("项目预算");
            assertThat(result).contains("第2行");
            assertThat(result).contains("第8行");
            assertThat(result).doesNotContain("第1行");
            assertThat(result).doesNotContain("第9行");
        }

        @Test
        @DisplayName("多关键词不导致行重复")
        void shouldNotDuplicateLinesForMultipleKeywords() {
            String text = String.join("\n", List.of(
                    "招标编号：XY-2026-001",
                    "项目名称：西域智能投标平台",
                    "预算金额：500万元"));

            String result = TenderIntakeTextProcessor.buildTenderIntakeCandidateText(text);

            // 验证每一行只出现一次：通过换行拆分后行数应等于原行数
            assertThat(result.split("\n")).hasSize(3);
            assertThat(result).contains("招标编号");
            assertThat(result).contains("项目名称");
            assertThat(result).contains("预算金额");
        }

        @Test
        @DisplayName("候选文本超过 20000 字符时截断")
        void shouldTruncateToMaxChars() {
            StringBuilder sb = new StringBuilder();
            sb.append("项目预算：100万元\n");
            // 构造大量带关键词的行，使候选文本超过 20000
            for (int i = 0; i < 500; i++) {
                sb.append("第").append(i).append("行采购内容：填充文本\n");
            }

            String result = TenderIntakeTextProcessor.buildTenderIntakeCandidateText(sb.toString());

            assertThat(result).hasSizeLessThanOrEqualTo(20_000);
            assertThat(result).startsWith("项目预算");
        }
    }

    // ── sanitizeUntrusted ──────────────────────────────────────────

    @Nested
    @DisplayName("sanitizeUntrusted")
    class SanitizeUntrusted {

        @Test
        @DisplayName("null → 空字符串")
        void shouldReturnEmptyForNull() {
            assertThat(TenderIntakeTextProcessor.sanitizeUntrusted(null)).isEmpty();
        }

        @Test
        @DisplayName("替换 document 标签为 HTML 实体")
        void shouldEscapeDocumentTags() {
            String raw = "<document>招标公告</document>";

            String result = TenderIntakeTextProcessor.sanitizeUntrusted(raw);

            assertThat(result).isEqualTo("&lt;document&gt;招标公告&lt;/document&gt;");
        }

        @Test
        @DisplayName("普通文本原样保留")
        void shouldPreserveNormalText() {
            String raw = "项目编号 XY-2026-001，预算 100 万元。";

            assertThat(TenderIntakeTextProcessor.sanitizeUntrusted(raw)).isEqualTo(raw);
        }
    }

    // ── parseSectionsFromMetadata ──────────────────────────────────

    @Nested
    @DisplayName("parseSectionsFromMetadata")
    class ParseSectionsFromMetadata {

        @Test
        @DisplayName("null / blank → 空字符串")
        void shouldReturnEmptyForNullOrBlank() {
            assertThat(TenderIntakeTextProcessor.parseSectionsFromMetadata(null)).isEqualTo("");
            assertThat(TenderIntakeTextProcessor.parseSectionsFromMetadata("")).isEqualTo("");
            assertThat(TenderIntakeTextProcessor.parseSectionsFromMetadata("  ")).isEqualTo("");
        }

        @Test
        @DisplayName("无 sections 字段 → 空字符串")
        void shouldReturnEmptyForNoSections() {
            String json = "{\"markdown\":\"正文\",\"converter\":\"markitdown\"}";
            assertThat(TenderIntakeTextProcessor.parseSectionsFromMetadata(json)).isEqualTo("");
        }

        @Test
        @DisplayName("空 sections 数组 → 空字符串")
        void shouldReturnEmptyForEmptySections() {
            String json = "{\"sections\":[]}";
            assertThat(TenderIntakeTextProcessor.parseSectionsFromMetadata(json)).isEqualTo("");
        }

        @Test
        @DisplayName("坏 JSON → 空字符串")
        void shouldReturnEmptyForMalformedJson() {
            assertThat(TenderIntakeTextProcessor.parseSectionsFromMetadata("not json")).isEqualTo("");
            assertThat(TenderIntakeTextProcessor.parseSectionsFromMetadata("{broken")).isEqualTo("");
        }

        @Test
        @DisplayName("标准 sections → 格式化标题结构")
        void shouldFormatSectionsCorrectly() {
            String json = """
                    {
                      "sections": [
                        {"heading": "项目概况", "level": 1, "charStart": 0, "charEnd": 100, "path": ["项目概况"]},
                        {"heading": "供应商核心条件", "level": 1, "charStart": 100, "charEnd": 200, "path": ["供应商核心条件"]},
                        {"heading": "资质要求", "level": 2, "charStart": 120, "charEnd": 160, "path": ["供应商核心条件", "资质要求"]},
                        {"heading": "时间", "level": 1, "charStart": 200, "charEnd": 300, "path": ["时间"]}
                      ]
                    }
                    """;
            String result = TenderIntakeTextProcessor.parseSectionsFromMetadata(json);

            assertThat(result).contains("文档标题结构");
            assertThat(result).contains("- 项目概况");
            assertThat(result).contains("- 供应商核心条件");
            assertThat(result).contains("  - 资质要求");  // level 2, 缩进 2 空格
            assertThat(result).contains("- 时间");
        }

        @Test
        @DisplayName("section 含空 heading → 跳过")
        void shouldSkipEmptyHeadings() {
            String json = """
                    {
                      "sections": [
                        {"heading": "项目概况", "level": 1},
                        {"heading": "", "level": 1},
                        {"heading": "时间", "level": 1}
                      ]
                    }
                    """;
            String result = TenderIntakeTextProcessor.parseSectionsFromMetadata(json);

            assertThat(result).contains("项目概况");
            assertThat(result).contains("时间");
            // 空 heading 不应出现空行
            assertThat(result).doesNotContain("- \n");
        }
    }
}