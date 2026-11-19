// Input: TableScoreExtractionPrompt 构建方法
// Output: 边界与断言验证
// Pos: scoreparse/infrastructure/openai
package com.xiyu.bid.scoreparse.infrastructure.openai;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TableScoreExtractionPromptTest {

    @Test
    void build_containsCoreRulesAndTableMarkdown() {
        String table = """
                | 序号 | 评审因素 | 评分标准 | 分值 |
                | 1 | 供应商业绩 | 提供同类业绩得7分 | 7 |
                """;

        String prompt = TableScoreExtractionPrompt.build(table);

        assertThat(prompt).contains("SCORE_TABLE");
        assertThat(prompt).contains("CONTRACT_ASSESSMENT");
        assertThat(prompt).contains("PROCEDURE_TABLE");
        assertThat(prompt).contains("detail 必须是表格单元格原文逐字复制");
        assertThat(prompt).contains("跨行合并单元格");
        assertThat(prompt).contains("括号分值提取");
        assertThat(prompt).contains("供应商业绩");
        assertThat(prompt).contains("deliverable_type");
        assertThat(prompt).contains("SYSTEM_SCREENSHOT");
        assertThat(prompt).contains("scoring_mode");
        assertThat(prompt).contains("QUANTITATIVE_RANKING");
    }

    @Test
    void build_stripsMarkdownAndBase64Images() {
        String trickyTable = "| 1 | 资质 | 详见 ![icon](data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==) 要求 | 5 |";

        String prompt = TableScoreExtractionPrompt.build(trickyTable);

        assertThat(prompt).doesNotContain("data:image/png;base64");
        assertThat(prompt).contains("详见");
        assertThat(prompt).contains("要求");
    }

    @Test
    void build_nullHandling() {
        String prompt = TableScoreExtractionPrompt.build(null);
        assertThat(prompt).contains("你是一个资深招标文件评分标准分析专家");
    }
}
