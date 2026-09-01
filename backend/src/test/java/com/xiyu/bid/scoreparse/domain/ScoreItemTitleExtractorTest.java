package com.xiyu.bid.scoreparse.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ScoreItemTitleExtractorTest {

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
    void oversizedPrefix_returnsEmpty() {
        String longPrefix = "A".repeat(45) + "：细则内容";
        assertThat(ScoreItemTitleExtractor.extract(longPrefix)).isEmpty();
    }
}
