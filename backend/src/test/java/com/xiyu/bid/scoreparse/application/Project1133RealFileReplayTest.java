package com.xiyu.bid.scoreparse.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.scoreparse.domain.ScoreParseDecision;
import com.xiyu.bid.scoreparse.domain.ScoreTypeClassificationPolicy;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.assertj.core.api.Assertions.assertThat;

/** Opt-in replay of private real inputs; customer documents are never checked into Git. */
@EnabledIfSystemProperty(named = "scoreparse.realArtifact", matches = ".+")
class Project1133RealFileReplayTest {
    @Test void actualPdfArtifactProducesTwelveSourceVerifiedRules() throws Exception {
        var artifact = new ObjectMapper().readValue(Path.of(System.getProperty("scoreparse.realArtifact")).toFile(),
                DocumentArtifact.class);
        String text = Files.readString(Path.of(System.getProperty("scoreparse.realText")));
        var result = StructuredArtifactScoreExtraction.extract(artifact, "tender.pdf", text);
        assertThat(result.completeness().decision()).as(result.completeness().defects().toString())
                .isEqualTo(ScoreParseDecision.AUTO_APPROVED);
        assertThat(result.originalVerified()).isTrue();
        assertThat(result.candidates()).hasSize(12);
        assertThat(result.completeness().leafWeightSum()).isEqualByComparingTo("100");
        List<String> expectedNames = List.of("投标报价", "类似业绩", "体系认证", "总体组织实施方案", "商品覆盖程度", "货物配送能力",
                "电商平台便捷性", "商品质量保障", "售后服务方案", "项目团队", "专属采购服务平台建设", "仓储情况");
        List<String> expectedWeights = List.of("30", "10", "6", "10", "12", "10", "3", "3", "3", "3", "5", "5");
        var facts = result.facts();
        assertThat(facts).hasSize(12);
        var classifier = new ScoreTypeClassificationPolicy();
        for (int i = 0; i < facts.size(); i++) {
            var f = facts.get(i);
            assertThat(f.dimension().replaceAll("\\s+", "")).isEqualTo(expectedNames.get(i));
            assertThat(f.weight()).isEqualByComparingTo(expectedWeights.get(i));
            assertThat(f.sourceText()).isNotBlank().doesNotContain("格式自拟", "合计");
            assertThat(f.sourceCellIds()).isNotEmpty();
            String type = List.of(3, 4, 6, 7, 8, 9).contains(i) ? "SUBJECTIVE" : "OBJECTIVE";
            assertThat(classifier.classify(f.sourceText())).as(f.dimension()).isEqualTo(type);
        }
        assertThat(facts.get(4).sourceText()).contains("4-0.1", "未提供");
        assertThat(facts.get(8).sourceText()).contains("退换货", "1-0.1", "未提供");
        assertThat(facts.get(0).sourceText()).contains("0.5", "0.3");
        assertThat(facts.get(1).sourceText()).contains("500", "第二个");
        assertThat(facts.get(10).sourceText()).contains("API", "承诺函");
    }
}
