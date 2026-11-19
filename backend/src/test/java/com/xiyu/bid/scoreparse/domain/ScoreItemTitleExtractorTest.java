package com.xiyu.bid.scoreparse.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ScoreItemTitleExtractorTest {

    @Test
    void htmlBreakOnlyRulesHaveNoTitleOrFallbackName() {
        for (String blankRule : java.util.List.of("<br>", "<BR /><br/>", " <br> <br> ")) {
            assertThat(ScoreItemTitleExtractor.extract(blankRule)).isEmpty();
            assertThat(ScoreItemTitleExtractor.resolveDimension("", blankRule)).isEmpty();
            var candidate = new ScoreCandidate("1", "", blankRule, java.math.BigDecimal.ONE,
                    "OBJECTIVE", null, blankRule, "", "SEMANTIC");
            assertThat(ScoreCategoryClassifier.isInvalidOrPlaceholderName(
                    ScoreItemTitleExtractor.resolveCandidateDimension(candidate, false))).isTrue();
        }
    }

    @Test
    void extractsNumberedTitleFromFirstLine() {
        assertThat(ScoreItemTitleExtractor.extract("1. 人员配置：具备相关经验。"))
                .contains("人员配置");
    }

    @Test
    void punctuationInScoringSentenceDoesNotRejectTitle() {
        assertThat(ScoreItemTitleExtractor.extract("人员配置：具备相关经验；满分5分。"))
                .contains("人员配置");
    }

    @Test
    void rejectsSentenceBeforeColonAndEmptyColonContent() {
        assertThat(ScoreItemTitleExtractor.extract("投标人应当提供人员配置，否则不得分："))
                .isEmpty();
        assertThat(ScoreItemTitleExtractor.extract("人员配置："))
                .isEmpty();
    }

    @Test
    void readsOnlyFirstMarkdownLine() {
        assertThat(ScoreItemTitleExtractor.extract("人员配置：提供项目经理<br>具备相关经验。"))
                .contains("人员配置");
    }

    @Test
    void nullAndBlankInputs_returnEmpty() {
        assertThat(ScoreItemTitleExtractor.extract(null)).isEmpty();
        assertThat(ScoreItemTitleExtractor.extract("")).isEmpty();
        assertThat(ScoreItemTitleExtractor.extract("   \t\n")).isEmpty();
        assertThat(ScoreItemTitleExtractor.extract("无冒号的评分细则原文")).isEmpty();
    }

    @Test
    void chineseNumberingAndEnglishColon() {
        assertThat(ScoreItemTitleExtractor.extract("第一条 服务方案: 具备相关技术架构。"))
                .contains("服务方案");
        assertThat(ScoreItemTitleExtractor.extract("（1）人员资质：具备PMP证书。"))
                .contains("人员资质");
    }

    @Test
    void extractsTitleFromNonColonFirstLineWhenBodyFollows() {
        String detail = "能够提供最长\n账期\n最长账期6个月（含）以上的，得5分；最长账期3个月(含)~6个月的，得3分；其余不得分。";
        assertThat(ScoreItemTitleExtractor.extract(detail)).contains("能够提供最长账期");

        String detail2 = "能否接受承兑\n接受银行/商业承兑的，得3分，否则不得分。";
        assertThat(ScoreItemTitleExtractor.extract(detail2)).contains("能否接受承兑");

        String detail3 = "仓储体系\n根据各投标人的仓储情况进行评分。\n(1)湖北省内设有仓库且仓储面积总和大于50000m及以上，得4分";
        assertThat(ScoreItemTitleExtractor.extract(detail3)).contains("仓储体系");

        String detail4 = "1.交货期限承诺(3分)\n送货地点属于县市城区范围的，投标人承诺自采购人下单起48小时内送达得3分";
        assertThat(ScoreItemTitleExtractor.extract(detail4)).contains("交货期限承诺");

        String detail5 = "2.物流体系(2分)\n根据各投标人物流体系进行评分。";
        assertThat(ScoreItemTitleExtractor.extract(detail5)).contains("物流体系");
    }

    @Test
    void colonWithNewlineBody() {
        assertThat(ScoreItemTitleExtractor.extract("人员配置：\n具备相关经验。"))
                .contains("人员配置");
    }

    @Test
    void oversizedPrefix_returnsEmpty() {
        String longPrefix = "A".repeat(45) + "：细则内容";
        assertThat(ScoreItemTitleExtractor.extract(longPrefix)).isEmpty();
    }

    @Test
    void cleansMalformedDimensionAndExtractsLeafFromPipeOrCategoryPrefixedDetail() {
        assertThat(ScoreItemTitleExtractor.cleanDimensionLabel("商务评分（20\n分）")).isEqualTo("商务评分");
        assertThat(ScoreItemTitleExtractor.cleanDimensionLabel("商务评分（20")).isEqualTo("商务评分");
        assertThat(ScoreItemTitleExtractor.cleanDimensionLabel("3 | 营业总收入")).isEqualTo("营业总收入");

        assertThat(ScoreItemTitleExtractor.extract("3 | 营业总收入 | 2024年营业总收入大于10亿元得5分"))
                .contains("营业总收入");
        assertThat(ScoreItemTitleExtractor.extract("商务评分（20\n分）\n1. 营业总收入：近三年年均营业总收入不低于5亿元。"))
                .contains("营业总收入");

        assertThat(ScoreItemTitleExtractor.resolveDimension("商务评分（20", "营业总收入：近三年高于5亿得5分"))
                .isEqualTo("营业总收入");
        assertThat(ScoreItemTitleExtractor.resolveDimension("商务评分（20", "无冒号的单纯描述文本"))
                .isEmpty();
        assertThat(ScoreItemTitleExtractor.resolveDimension("商务评分（20\n分）", "无冒号的单纯描述文本"))
                .isEqualTo("商务评分");
        assertThat(ScoreItemTitleExtractor.resolveDimension("1", "无冒号的单纯描述文本"))
                .isEmpty();
    }
    @Test
    void publicationRequiresSpecificTitleForUnclosedDimension() {
        assertThat(OriginalScoreEvidencePolicy.publishedDimension(
                "商务评分（20", "投标人提供营业收入证明得30分", java.math.BigDecimal.valueOf(30))).isEmpty();
        assertThat(OriginalScoreEvidencePolicy.publishedDimension(
                "商务评分（20", "营业总收入（30分）：投标人提供营业收入证明得30分",
                java.math.BigDecimal.valueOf(30))).isEqualTo("营业总收入");
    }

    @Test
    void legacyCandidateWithUnclosedDimensionNeedsAnExtractedTitle() {
        String body = "投标人提供营业收入证明得30分";
        var unrecoverable = legacyCandidateWithTruncatedName(body);
        var recoverable = legacyCandidateWithTruncatedName("营业总收入（30分）：" + body);

        assertThat(ScoreItemTitleExtractor.resolveCandidateDimension(unrecoverable, false)).isEmpty();
        assertThat(ScoreItemTitleExtractor.resolveCandidateDimension(recoverable, false)).isEqualTo("营业总收入");
    }

    private ScoreCandidate legacyCandidateWithTruncatedName(String detail) {
        return new ScoreCandidate("1", "商务评分（20", detail, java.math.BigDecimal.valueOf(30),
                "OBJECTIVE", null, detail, "", "SEMANTIC");
    }
}
