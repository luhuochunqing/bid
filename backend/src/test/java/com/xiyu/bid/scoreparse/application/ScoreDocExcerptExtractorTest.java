// Input: 投标文件全文 + 评分维度/细则（或 ScoreItem）
// Output: 关键词命中最相关的段落摘录，控制在 maxChars 预算内
// Pos: scoreparse/application — ScoreDocExcerptExtractor 单元测试（spec 049 T002）
package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.scoreparse.entity.ScoreItem;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ScoreDocExcerptExtractorTest {

    @Test
    void shortText_returnsAsIs() {
        String text = "简短文本";

        assertThat(ScoreDocExcerptExtractor.extractRelevantExcerpt(text, "维度", "细则", 1000))
                .isEqualTo(text);
        assertThat(ScoreDocExcerptExtractor.extractRelevantExcerpt(text, "维度", "细则", text.length()))
                .isEqualTo(text);
    }

    @Test
    void blankOrNullInput_returnsEmpty() {
        assertThat(ScoreDocExcerptExtractor.extractRelevantExcerpt("", "维度", "细则", 100)).isEmpty();
        assertThat(ScoreDocExcerptExtractor.extractRelevantExcerpt(null, "维度", "细则", 100)).isEmpty();
        assertThat(ScoreDocExcerptExtractor.extractRelevantExcerpt("  \t\n ", "维度", "细则", 100)).isEmpty();
        // 无关键词（dim/detail 均空）且全文超预算：走头尾段落兜底，不抛异常
        assertThat(ScoreDocExcerptExtractor.extractRelevantExcerpt("a\n\nb\n\nc", null, null, 1)).isNotEmpty();
    }

    @Test
    void dimKeywordScoring_keepsHittingParagraphAndNeighborsWithinBudget() {
        // 12 个等长段落，第 6 段（下标 5）命中 dim 关键词
        StringBuilder full = new StringBuilder();
        for (int i = 0; i < 12; i++) {
            if (i > 0) {
                full.append("\n\n");
            }
            full.append(String.format("段落%02d-" + "字".repeat(18), i));
        }
        String text = full.toString();
        int maxChars = 100;

        String result = ScoreDocExcerptExtractor.extractRelevantExcerpt(text, "段落05", "无关细则", maxChars);

        assertThat(result).contains("段落05");
        assertThat(result.length()).isLessThanOrEqualTo(maxChars);
        // 命中段落及其相邻段被选中
        assertThat(result).contains("段落04").contains("段落06");
        // 预算截断生效：不会把距离远的无关段落（如下标 9）拖进来
        assertThat(result).doesNotContain("段落09");
    }

    @Test
    void detailTokens_alsoScoreParagraphs() {
        // dim 未命中，但 detail 拆分出的「售后服务网络」token 命中第 4 段（下标 3）
        String text = String.join("\n\n",
                "方案介绍",           // p0
                "资质证明文件",       // p1
                "实施计划与交付",     // p2
                "本公司提供完善的售后服务网络，覆盖全国。", // p3 命中
                "项目团队配置",       // p4
                "培训与验收方案",     // p5
                "结语感谢");          // p6
        int maxChars = 40; // < 全文 64，触发关键词路径；足以容纳 p2+p3 但装不下 p4

        String result = ScoreDocExcerptExtractor.extractRelevantExcerpt(text, "商务标", "售后服务网络", maxChars);

        assertThat(result).contains("售后服务网络");
        assertThat(result).contains("本公司提供完善的售后服务");
        assertThat(result).doesNotContain("方案介绍");
        assertThat(result).doesNotContain("结语感谢");
    }

    @Test
    void dimDetailOverload_equivalentToScoreItemOverload() {
        String text = "首段\n\n中段包含关键内容售后服务承诺书。\n\n尾段";
        String dim = "售后服务";
        String detail = "承诺书";
        int maxChars = 30;

        String viaStrings = ScoreDocExcerptExtractor.extractRelevantExcerpt(text, dim, detail, maxChars);
        ScoreItem item = ScoreItem.builder().dim(dim).detail(detail).build();
        String viaItem = ScoreDocExcerptExtractor.extractRelevantExcerpt(text, item, maxChars);

        assertThat(viaStrings).isEqualTo(viaItem).contains("售后服务");
    }

    @Test
    void nullItem_returnsEmpty() {
        assertThat(ScoreDocExcerptExtractor.extractRelevantExcerpt("全文内容", null, 100)).isEmpty();
    }
}
