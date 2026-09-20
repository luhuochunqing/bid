package com.xiyu.bid.scoreparse.domain;

import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class MultimodalScoreFactValidatorTest {
    private final MultimodalScoreFactValidator validator = new MultimodalScoreFactValidator();

    @Test void approvesCompleteVerbatimScoringSet() {
        var result = validator.validate(List.of(
                fact("商务", "1", null, "报价", "30", "报价按公式计算，满分30分", "c1"),
                fact("商务", "2", null, "销售额", "70", "销售额达到1亿元得70分", "c2")),
                artifact(cell("c1", "报价按公式计算，满分30分"), cell("c2", "销售额达到1亿元得70分")), new BigDecimal("100"));
        assertThat(result.autoApprovable()).isTrue();
        assertThat(result.violations()).isEmpty();
    }

    @Test void reportsAllHardDifferencesWithoutDroppingCandidates() {
        var result = validator.validate(List.of(
                fact("商务", "2", null, "报价", "30", "不存在的改写", "c1"),
                fact("商务", "2", null, "重复", "20", "原文二", "c2")),
                artifact(cell("c1", "原文一"), cell("c2", "原文二")), new BigDecimal("100"));
        assertThat(result.autoApprovable()).isFalse();
        assertThat(result.facts()).hasSize(2);
        assertThat(result.violations()).extracting(MultimodalScoreFactValidator.Violation::code)
                .contains("SEQUENCE_GAP", "DUPLICATE_CODE", "WEIGHT_SUM_MISMATCH", "QUOTE_NOT_VERBATIM");
    }

    @Test void validatesParentWeightButCountsOnlyLeaves() {
        var result = validator.validate(List.of(
                fact("技术", "1", null, "方案", "40", "方案满分40分", "p"),
                fact("技术", "1.1", "1", "实施", "10", "实施得10分", "c1"),
                fact("技术", "1.2", "1", "服务", "20", "服务得20分", "c2")),
                artifact(cell("p", "方案满分40分"), cell("c1", "实施得10分"), cell("c2", "服务得20分")), new BigDecimal("30"));
        assertThat(result.violations()).extracting(MultimodalScoreFactValidator.Violation::code)
                .contains("PARENT_WEIGHT_MISMATCH");
        assertThat(result.leafTotal()).isEqualByComparingTo("30");
    }

    @Test void rejectsEmptyFactsEvenWhenDeclaredTotalIsZero() {
        var result = validator.validate(List.of(), artifact(), BigDecimal.ZERO);
        assertThat(result.autoApprovable()).isFalse();
        assertThat(result.violations()).extracting(MultimodalScoreFactValidator.Violation::code)
                .contains("EMPTY_FACTS");
    }

    @Test void rejectsMissingRuleDimensionAndInvalidScoreType() {
        var invalid = new MultimodalScoreFact("商务", "1", "", "", BigDecimal.TEN, "", "MIXED",
                3, "t1", List.of("c1"), "原文", 0.9);
        var result = validator.validate(List.of(invalid), artifact(cell("c1", "原文")), BigDecimal.TEN);
        assertThat(result.violations()).extracting(MultimodalScoreFactValidator.Violation::code)
                .contains("DIMENSION_MISSING", "RULE_MISSING", "SCORE_TYPE_INVALID");
    }

    private static MultimodalScoreFact fact(String section, String code, String parent, String name,
                                             String weight, String quote, String cellId) {
        return new MultimodalScoreFact(section, code, parent, name, new BigDecimal(weight), quote,
                "OBJECTIVE", 3, "t1", List.of(cellId), quote, 0.9);
    }
    private static DocumentArtifact artifact(DocumentArtifact.CanonicalTable.CanonicalCellDto... cells) {
        return new DocumentArtifact(new DocumentArtifact.ArtifactMeta("d", "sha", "docx", "v2"),
                List.of(new DocumentArtifact.CanonicalTable("t1", 3, "商务", 3, 2, List.of(), List.of(cells), true)), List.of());
    }
    private static DocumentArtifact.CanonicalTable.CanonicalCellDto cell(String id, String text) {
        return new DocumentArtifact.CanonicalTable.CanonicalCellDto(id, 1, 1, text, text, 1, 1, false, "");
    }
}
