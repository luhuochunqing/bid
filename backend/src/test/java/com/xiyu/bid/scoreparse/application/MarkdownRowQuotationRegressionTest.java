package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.scoreparse.domain.*;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/** Format invariance across real binding, rule restoration and manual adoption. */
class MarkdownRowQuotationRegressionTest {
    private static final String RULE = "审计报告完整得5分，否则0分。";
    private static final String FORMULA = "得分=5×|报价-基准|/基准，最高5分。";

    static Stream<Arguments> rowQuotes() {
        return Stream.of(RULE, FORMULA, "MRO商品目录完整得5分，目录缺失得0分。").flatMap(rule -> Stream.of(
                Arguments.of("| **经营能力** | **5** | " + rule + " |", rule),
                Arguments.of("**经营能力** | **5** | " + rule, rule),
                Arguments.of("| **经营能力** | **5** | " + rule, rule),
                Arguments.of("**经营能力** | **5** | " + rule + " |", rule)));
    }

    @ParameterizedTest @MethodSource("rowQuotes")
    void optionalOuterSeparatorsPreserveCompleteRuleAndFormula(String quote, String rule) {
        var artifact = artifact(rule, false, true);
        var fact = fact(quote);
        var bound = TextPrimaryEvidenceBinder.bind(List.of(fact), artifact, "").getFirst();
        assertThat(bound.sourceCellIds()).containsExactly("t-r1-c0", "t-r1-c1", "t-r1-c2");
        var validation = new MultimodalScoreFactValidator().validate(List.of(bound), artifact, BigDecimal.valueOf(5));
        assertThat(validation.violations()).noneMatch(v -> "QUOTE_NOT_VERBATIM".equals(v.code()));
        var mapped = TextPrimaryScoreFactMapper.map(bound, artifact, validation);
        assertThat(mapped.sourceText()).isEqualTo("经营能力\n5\n" + rule);
        assertThat(mapped.dimension()).isEqualTo("经营能力");
        assertThat(mapped.weight()).isEqualByComparingTo("5");
        assertThat(mapped.sourceMetadata().modelQuotedText()).isEqualTo(quote);
        var adopted = new ScoreReviewAdoptionPolicy().adopt(List.of(
                new ScoreReviewAdoptionPolicy.CandidateInput(mapped.factId(), mapped.code(), mapped.dimension(),
                        mapped.weight(), mapped.sourceText(), "")), Set.of(mapped.factId()), Map.of());
        assertThat(adopted.success()).isTrue();
        assertThat(adopted.candidates().getFirst().detail()).isEqualTo(mapped.sourceText());
    }

    @ParameterizedTest @MethodSource("rowQuotes")
    void separatorVariantsCannotBypassAmbiguityScopeOrSourceClosure(String quote, String rule) {
        assertThat(TextPrimaryEvidenceBinder.bind(List.of(fact(quote)), artifact(rule, true, true), "")
                .getFirst().sourceCellIds()).isEmpty();
        assertThat(TextPrimaryEvidenceBinder.bind(List.of(fact(quote)), artifact(rule, false, false), "")
                .getFirst().sourceCellIds()).isEmpty();
        assertThat(TextPrimaryEvidenceBinder.bind(List.of(fact(quote).withScope("other", "IN_SCHEME")),
                artifact(rule, false, true), "").getFirst().sourceCellIds()).isEmpty();
    }

    @Test void alteredScoreAndInternalFormulaCannotBindAsLayoutVariants() {
        var artifact = artifact(FORMULA, false, true);
        for (String quote : List.of("经营能力 | 6 | " + FORMULA,
                "经营能力 | 5 | " + FORMULA.replace("|报价-基准|", "报价-基准"),
                "经营能力 | 5 | " + FORMULA.replace("最高5分", "最高6分"))) {
            for (String dimension : List.of("商务", "经营能力")) {
                assertThat(TextPrimaryEvidenceBinder.bind(List.of(fact(quote, dimension)), artifact, "")
                        .getFirst().sourceCellIds()).isEmpty();
            }
        }
    }

    @Test void absoluteValueFormulaRetainsExactCrossTableContinuationBinding() {
        String first = "得分=5×|报价-";
        String second = "基准|/基准，最高5分。";
        var firstCells = List.of(continuationCell("id", 1, 0, "3"),
                continuationCell("name", 1, 1, "实施方案"), continuationCell("rule1", 1, 2, first));
        var nextCells = List.of(continuationCell("next-id", 0, 0, ""),
                continuationCell("next-name", 0, 1, ""), continuationCell("rule2", 0, 2, second));
        var artifact = new DocumentArtifact(new DocumentArtifact.ArtifactMeta("synthetic", "sha", "DOCX", "v2"),
                List.of(new DocumentArtifact.CanonicalTable("t1", null, "第三章 评审标准", 2, 3,
                        List.of(), firstCells, true),
                        new DocumentArtifact.CanonicalTable("t2", null, "第三章 评审标准", 1, 3,
                                List.of(), nextCells, false)), List.of());
        var claim = new MultimodalScoreFact("第三章 评审标准", "3", "", "实施方案", BigDecimal.valueOf(5),
                first + second, "OBJECTIVE", null, "", List.of(), first + second, .9, ChildAggregation.SUM);
        var bound = TextPrimaryEvidenceBinder.bind(List.of(claim), artifact, "").getFirst();
        assertThat(bound.sourceCellIds()).containsExactly("rule1", "rule2");
        assertThat(bound.quotedText()).isEqualTo(first + second);
    }

    private static DocumentArtifact.CanonicalTable.CanonicalCellDto continuationCell(
            String id, int row, int column, String raw) {
        return new DocumentArtifact.CanonicalTable.CanonicalCellDto(id, row, column, raw, raw,
                1, 1, false, "/" + id, null, "shared-row");
    }

    private static MultimodalScoreFact fact(String quote) {
        return fact(quote, "商务");
    }
    private static MultimodalScoreFact fact(String quote, String dimension) {
        return new MultimodalScoreFact("第三章 评审办法", "finance", "", dimension, BigDecimal.valueOf(5),
                "模型摘要", "SUBJECTIVE", null, "", List.of(), quote, .9);
    }

    private static DocumentArtifact artifact(String rule, boolean repeated, boolean closed) {
        var cells = new ArrayList<DocumentArtifact.CanonicalTable.CanonicalCellDto>();
        String[][] rows = {{"评审项目", "分值", "评审标准"}, {"经营能力", "5", rule}};
        int count = repeated ? 3 : 2;
        for (int row = 0; row < count; row++) for (int col = 0; col < 3; col++) {
            String raw = rows[Math.min(row, 1)][col];
            cells.add(new DocumentArtifact.CanonicalTable.CanonicalCellDto("t-r" + row + "-c" + col,
                    row, col, raw, raw, 1, 1, false,
                    closed ? "/w:tbl[1]/w:tr[" + (row + 1) + "]/w:tc[" + (col + 1) + "]" : "",
                    null, "t-r" + row));
        }
        return new DocumentArtifact(new DocumentArtifact.ArtifactMeta("synthetic", "sha", "DOCX", "v2"),
                List.of(new DocumentArtifact.CanonicalTable("t", null, "第三章 评审办法", count, 3,
                        List.of(), cells, true)), List.of());
    }
}
