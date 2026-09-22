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
                sourceFact("1", "10", "c1-rule"), sourceFact("2", "90", "c2-rule")),
                sourceBoundArtifact(), new BigDecimal("100"));
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

    @Test void rejectsModelTotalShrunkToMatchMissingItem() {
        var report = validator.validate(List.of(sourceFact("1", "10", "c1-rule")),
                sourceBoundArtifact(), new BigDecimal("10"));

        assertThat(report.autoApprovable()).isFalse();
        assertThat(report.violations()).extracting(MultimodalScoreFactValidator.Violation::code)
                .contains("MODEL_TOTAL_SOURCE_MISMATCH");
        assertThat(report.violations()).anySatisfy(violation -> {
            if ("MODEL_TOTAL_SOURCE_MISMATCH".equals(violation.code())) {
                assertThat(violation.expected()).isEqualTo(new BigDecimal("100"));
                assertThat(violation.actual()).isEqualTo(new BigDecimal("10"));
            }
        });
    }

    @Test void rejectsOneItemInflatedToMatchOriginalTotal() {
        var report = validator.validate(List.of(sourceFact("1", "100", "c1-rule")),
                sourceBoundArtifact(), new BigDecimal("100"));

        assertThat(report.autoApprovable()).isFalse();
        assertThat(report.violations()).extracting(MultimodalScoreFactValidator.Violation::code)
                .contains("ITEM_WEIGHT_SOURCE_MISMATCH");
    }

    @Test void rejectsCompensatingItemErrorsEvenWhenLeafSumIsCorrect() {
        var report = validator.validate(List.of(sourceFact("1", "20", "c1-rule"),
                sourceFact("2", "80", "c2-rule")), sourceBoundArtifact(), new BigDecimal("100"));

        assertThat(report.leafTotal()).isEqualByComparingTo("100");
        assertThat(report.autoApprovable()).isFalse();
        assertThat(report.violations()).filteredOn(v -> "ITEM_WEIGHT_SOURCE_MISMATCH".equals(v.code()))
                .hasSize(2);
    }

    private static MultimodalScoreFact sourceFact(String code, String weight, String ruleCellId) {
        String rule = "1".equals(code) ? "销售额达到1亿元得10分" : "技术方案按完整性与可行性综合评议，最高得90分";
        String name = "1".equals(code) ? "销售额" : "技术方案";
        return new MultimodalScoreFact("本项目评分办法", code, "", name, new BigDecimal(weight), rule,
                "OBJECTIVE", 3, "score-t1", List.of(ruleCellId), rule, 0.9);
    }

    private static DocumentArtifact sourceBoundArtifact() {
        var cells = List.of(
                sourceCell("total", 0, 0, "本项目评分总分：100分"),
                sourceCell("c1-name", 1, 0, "销售额"),
                sourceCell("c1-rule", 1, 1, "销售额达到1亿元得10分"),
                sourceCell("c1-weight", 1, 2, "10分"),
                sourceCell("c2-name", 2, 0, "技术方案"),
                sourceCell("c2-rule", 2, 1, "技术方案按完整性与可行性综合评议，最高得90分"),
                sourceCell("c2-weight", 2, 2, "90分"));
        return new DocumentArtifact(new DocumentArtifact.ArtifactMeta("bid.pdf", "sha", "pdf", "2.0"),
                List.of(new DocumentArtifact.CanonicalTable("score-t1", 3, "本项目评分办法", 3, 3,
                        List.of(), cells, true)), List.of());
    }

    private static DocumentArtifact.CanonicalTable.CanonicalCellDto sourceCell(
            String id, int row, int column, String text) {
        return new DocumentArtifact.CanonicalTable.CanonicalCellDto(id, row, column,
                text, text, 1, 1, false, "");
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
