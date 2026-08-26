// Input: TextChunker.split（docinsight 通用切片器）
// Output: 表格行安全边界回归锁定（spec 055 FR-4：切分边界永不落在管道表格行内部）
// Pos: Test/docinsight/domain
// 维护声明: 维护者按项目SOP；spec 055 评审 M1 结论——FR-4 由 TextChunker 既有
//   lastIndexOf('\n') 行边界回退满足，本测试锁定该行为防回归（ScoreTableAwareChunker 死代码已删）

package com.xiyu.bid.docinsight.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TextChunkerTest {

    private static String scoreTableRows(int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= count; i++) {
            sb.append("| 评分项").append(i)
              .append(" | 评审内容描述若干文字用于撑长度测试边界行为是否落在行内").append(i)
              .append(" | ").append(i % 10).append(" |\n");
        }
        return sb.toString();
    }

    @Test
    void split_tableText_boundariesNeverInsidePipeRow() {
        String text = "评分办法表：\n" + scoreTableRows(40);
        List<String> chunks = TextChunker.split(text, 400, 0);
        assertThat(chunks.size()).isGreaterThan(1);
        for (String chunk : chunks) {
            for (String line : chunk.split("\n", -1)) {
                String trimmed = line.trim();
                if (trimmed.startsWith("|")) {
                    assertThat(trimmed.endsWith("|"))
                            .as("表格行被拦腰斩断: %s", trimmed)
                            .isTrue();
                }
            }
        }
        // 无内容丢失（overlap=0 时拼接还原原文）
        assertThat(String.join("", chunks)).isEqualTo(text);
    }

    @Test
    void split_noNewlineOversizedText_hardSplitFallback() {
        String text = "无换行连续文本".repeat(200);
        List<String> chunks = TextChunker.split(text, 100, 0);
        assertThat(chunks.size()).isGreaterThan(1);
        int totalLength = chunks.stream().mapToInt(String::length).sum();
        assertThat(totalLength).isGreaterThanOrEqualTo(text.length());
    }
}
