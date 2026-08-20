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

    @Test
    void tocAnchorParagraphs_areExcludedFromEvidence() {
        // 模拟 401MB 标书 markdown 的形态：Word 自动目录锚点段（含评分项标题，但只有导航无正文证据）
        // + 正文证据段。TOC 段不得被当成证据送进摘录。全文长度必须超预算才会走关键词路径。
        String text = String.join("\n\n",
                "[1. 公司综合实力 1978](#_Toc232494505)",          // TOC 锚点：命中关键词但无证据
                "第一章 公司基本情况",                                // 正文章节标题
                "本公司注册于上海，成立十年，注册资本一亿元，拥有多项行业资质，综合实力雄厚。", // 真实证据
                "[2. 项目标准和要求承诺函 1990](#_Toc232494506)",   // TOC 锚点
                "结语");
        int maxChars = 60; // < 全文长度，触发关键词打分路径

        String result = ScoreDocExcerptExtractor.extractRelevantExcerpt(text, "公司综合实力", "注册资本资质", maxChars);

        assertThat(result).contains("注册资本一亿元").contains("综合实力雄厚");
        assertThat(result).doesNotContain("#_Toc");
    }

    @Test
    void allTocAnchors_fallbackStillNonEmpty() {
        // 极端退化：全文都是 TOC 锚点段（无任何正文），不抛异常、不把锚点当证据得分
        String text = "[1. 公司综合实力 1978](#_Toc1)\n\n[2. 售后服务方案 1990](#_Toc2)\n\n[3. 技术评审 2001](#_Toc3)";
        String result = ScoreDocExcerptExtractor.extractRelevantExcerpt(text, "公司综合实力", "资质", 20);
        // 无正文可提取时返回空（诚实：无可用摘录），而非把目录锚点当证据
        assertThat(result).isEmpty();
    }
}
