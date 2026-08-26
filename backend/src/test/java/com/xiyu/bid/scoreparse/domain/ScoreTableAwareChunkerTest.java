// Input: ScoreTableAwareChunker（rechunk 方法）+ DocumentChunk
// Output: 表格行安全边界重切行为验证（spec 055 SC-3 / FR-4）
// Pos: Test/scoreparse/domain

package com.xiyu.bid.scoreparse.domain;

import com.xiyu.bid.docinsight.domain.DocumentChunk;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ScoreTableAwareChunkerTest {

    private static final int MAX = 120;

    private static String tableRows(int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= count; i++) {
            sb.append("| 评分项").append(i)
              .append(" | 评审内容描述若干文字用于撑长度测试边界行为").append(i)
              .append(" | ").append(i % 10).append(" |\n");
        }
        return sb.toString();
    }

    @Test
    void rechunk_oversizedTableChunk_boundariesNeverInsideRow() {
        String table = tableRows(12);
        DocumentChunk oversized = new DocumentChunk("表头\n" + table, List.of("评分办法"));
        List<DocumentChunk> result = ScoreTableAwareChunker.rechunk(List.of(oversized), MAX);
        assertThat(result.size()).isGreaterThan(1);
        for (DocumentChunk chunk : result) {
            assertThat(chunk.text().length()).isLessThanOrEqualTo(MAX + 60);
            List<String> lines = List.of(chunk.text().split("\n", -1));
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.startsWith("|")) {
                    // 每个表格行必须完整（以 | 开头也以 | 结尾，未被拦腰斩断）
                    assertThat(trimmed.endsWith("|"))
                            .as("行被切断: %s", trimmed)
                            .isTrue();
                }
            }
        }
        // 无内容丢失：所有行都在结果中
        String joined = result.stream().map(DocumentChunk::text).reduce("", String::concat);
        assertThat(joined).contains("评分项12");
        assertThat(joined).contains("评分项1 ");
    }

    @Test
    void rechunk_smallOrNonTableChunk_unchanged() {
        DocumentChunk small = new DocumentChunk("| 评分项 | 分值 |\n| --- | --- |\n| 质量保障 | 10 |", List.of());
        DocumentChunk plain = new DocumentChunk("纯文本段落".repeat(50), List.of("正文"));
        List<DocumentChunk> result = ScoreTableAwareChunker.rechunk(List.of(small, plain), MAX);
        assertThat(result).hasSize(2);
        assertThat(result.get(0).text()).isEqualTo(small.text());
        assertThat(result.get(1).text()).isEqualTo(plain.text());
    }

    @Test
    void rechunk_nullOrEmpty_returnsAsIs() {
        assertThat(ScoreTableAwareChunker.rechunk(null, MAX)).isEmpty();
        assertThat(ScoreTableAwareChunker.rechunk(List.of(), MAX)).isEmpty();
    }
}
