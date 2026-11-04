package com.xiyu.bid.scoreparse.infrastructure.openai;

import com.xiyu.bid.scoreparse.application.MultimodalScoreFactExtractor;
import com.xiyu.bid.scoreparse.domain.MultimodalScoreFact;
import com.xiyu.bid.scoreparse.domain.ChildAggregation;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ScoreFactChunkMergerTest {
    @Test void exactModelFieldsInDifferentDocumentsMustSurviveTheEarlyMergeKey() {
        var first = withDocument(fact("1", "5", .8), "a");
        var second = withDocument(fact("1", "5", .9), "b");
        assertThat(ScoreFactChunkMerger.merge(List.of(result(null, first), result(null, second))).facts())
                .containsExactly(first, second);
    }

    private static MultimodalScoreFact withDocument(MultimodalScoreFact fact, String document) {
        var span = new com.xiyu.bid.scoreparse.domain.SourceSpan(document, document + "-sha", "PDF", "", 1,
                fact.tableId(), fact.sourceCellIds().getFirst(), 0, fact.quotedText().length(), fact.quotedText(), "pdf:page=1");
        return new MultimodalScoreFact(fact.sectionTitle(), fact.code(), fact.parentCode(), fact.dimension(), fact.weight(),
                fact.ruleText(), fact.scoreType(), fact.pageNumber(), fact.tableId(), fact.sourceCellIds(), fact.quotedText(),
                fact.confidence(), fact.childAggregation(), fact.schemeId(), fact.scopeStatus(),
                new com.xiyu.bid.scoreparse.domain.ScoreSourceMetadata(fact.ruleText(), fact.quotedText(), List.of(span), List.of()));
    }

    @Test
    void sameSourceRuleWithRenamedCodeAndLostParentIsCountedOnce() {
        var first = fact("service", "5", .8).withParentCode("business");
        var alias = new MultimodalScoreFact("第三章", "5", "", "服务", new BigDecimal("5.00"),
                first.ruleText(), "SUBJECTIVE", 1, first.tableId(), first.sourceCellIds(),
                first.quotedText(), .95, ChildAggregation.UNKNOWN);

        var facts = ScoreFactChunkMerger.merge(List.of(result(null, first), result(null, alias))).facts();

        assertThat(facts).hasSize(1);
        assertThat(facts.getFirst().parentCode()).isEqualTo("business");
        assertThat(facts.getFirst().ruleText()).isEqualTo(first.ruleText());
    }

    @Test
    void distinctQuotedSubRulesInOneCellAreNotCollapsed() {
        var first = fact("1", "5", .8);
        var second = new MultimodalScoreFact("技术", "2", "", "人员", new BigDecimal("5"),
                "人员至少5名得5分", "OBJECTIVE", 1, first.tableId(), first.sourceCellIds(),
                "人员至少5名得5分", .9, ChildAggregation.SUM);
        assertThat(ScoreFactChunkMerger.merge(List.of(result(null, first, second))).facts())
                .containsExactly(first, second);
    }

    @Test
    void weightConflictAtSameSourceIsNotSilentlyDiscarded() {
        var first = fact("1", "5", .8);
        var conflict = fact("1", "6", .9);
        assertThat(ScoreFactChunkMerger.merge(List.of(result(null, first, conflict))).facts()).hasSize(2);
    }

    @Test
    void removesExactOverlapDuplicateButKeepsDistinctFacts() {
        var first = result(BigDecimal.valueOf(100), fact("1", "5", 0.8), fact("2", "3", 0.8));
        var overlap = result(BigDecimal.valueOf(100), fact("1", "5", 0.9), fact("3", "2", 0.8));

        var merged = ScoreFactChunkMerger.merge(List.of(first, overlap));

        assertThat(merged.facts()).hasSize(3);
        assertThat(merged.declaredTotal()).isEqualByComparingTo("100");
    }

    @Test
    void conflictingDeclaredTotalsKeepFactsAndMarkTotalUnknown() {
        var merged = ScoreFactChunkMerger.merge(List.of(
                result(BigDecimal.valueOf(100), fact("1", "5", 0.8)),
                result(BigDecimal.valueOf(80), fact("2", "3", 0.8))));

        assertThat(merged.declaredTotal()).isNull();
        assertThat(merged.facts()).extracting(MultimodalScoreFact::code)
                .containsExactly("1", "2");
    }

    @Test
    void sameCodesFromDifferentSchemesAndAuditRangesAreNotCollapsed() {
        var inScheme = fact("1", "5", 0.8).withScope("scheme-a", "IN_SCHEME");
        var otherScheme = fact("1", "5", 0.9).withScope("scheme-b", "IN_SCHEME");
        var audit = fact("1", "5", 0.95).withScope("scheme-a", "OUT_OF_SCHEME");

        var merged = ScoreFactChunkMerger.merge(List.of(result(null, inScheme, otherScheme, audit)));

        assertThat(merged.facts()).hasSize(3);
        assertThat(merged.facts()).extracting(MultimodalScoreFact::scopeStatus)
                .containsExactly("IN_SCHEME", "IN_SCHEME", "OUT_OF_SCHEME");
    }

    private static MultimodalScoreFactExtractor.ExtractionResult result(BigDecimal total, MultimodalScoreFact... facts) {
        return new MultimodalScoreFactExtractor.ExtractionResult(total, List.of(facts), "model", 0);
    }

    private static MultimodalScoreFact fact(String code, String weight, double confidence) {
        return new MultimodalScoreFact("技术", code, "", "项" + code, new BigDecimal(weight), "完整规则" + code,
                "OBJECTIVE", 1, "table", List.of("cell-" + code), "逐字原文" + code, confidence,
                ChildAggregation.SUM);
    }
}
