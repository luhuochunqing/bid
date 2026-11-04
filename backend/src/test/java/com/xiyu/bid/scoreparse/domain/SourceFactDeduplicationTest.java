package com.xiyu.bid.scoreparse.domain;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class SourceFactDeduplicationTest {
    @Test void indistinguishableParentAliasesCannotEraseAUnaryChildInAnyInputOrder() {
        var a = fact("A", "", "证书得5分", "证书得5分", "doc");
        var b = fact("B", "", "证书得5分", "证书得5分", "doc");
        var child = fact("C", "B", "证书得5分", "证书得5分", "doc");
        for (var input : List.of(List.of(a, b, child), List.of(a, child, b), List.of(b, a, child),
                List.of(b, child, a), List.of(child, a, b), List.of(child, b, a))) {
            // The source unit is shared by two hierarchy levels: retain the ambiguity for review.
            assertThat(SourceFactDeduplication.merge(input)).containsExactlyElementsOf(input);
        }
    }
    @Test void threeLevelUnaryChainDoesNotCollapseItsAncestorIntoACycle() {
        var parent = fact("parent", "", "证书得5分", "证书得5分", "doc");
        var middle = fact("middle", "parent", "证书得5分", "证书得5分", "doc");
        var leaf = fact("leaf", "middle", "证书得5分", "证书得5分", "doc");
        assertThat(SourceFactDeduplication.merge(List.of(parent, middle, leaf))).containsExactly(parent, middle, leaf);
    }
    @Test void missingRuleCannotProveWhichSharedCellSubItemWasExtracted() {
        var first = fact("1", "", "证书得5分", "证书得5分；人员得5分", "doc");
        var incomplete = fact("2", "", "", "证书得5分；人员得5分", "doc");
        assertThat(SourceFactDeduplication.merge(List.of(first, incomplete))).containsExactly(first, incomplete);
    }

    @Test void oneMissingDocumentProofCannotEraseAnotherDocument() {
        var known = fact("1", "", "完整规则5分", "完整规则5分", "doc-A");
        var unknown = fact("2", "", "完整规则5分", "完整规则5分", "");
        assertThat(SourceFactDeduplication.merge(List.of(known, unknown))).containsExactly(known, unknown);
    }

    @Test void differentOriginalQuotesSurviveExpansionToTheSameWholeRow() {
        var first = fact("1", "", "证书得5分", "证书得5分", "doc");
        var second = fact("2", "", "人员得5分", "人员得5分", "doc");
        assertThat(SourceFactDeduplication.merge(List.of(first, second))).containsExactly(first, second);
    }

    @Test void aliasParentCoalescesWithoutOrphaningItsChild() {
        var parent = fact("parent", "", "总分5分", "总分5分", "doc");
        var alias = fact("P", "", "总分5分", "总分5分", "doc");
        var child = fact("child", "P", "证书得5分", "证书得5分", "doc");
        var actual = SourceFactDeduplication.merge(List.of(parent, alias, child));
        assertThat(actual).hasSize(2);
        assertThat(actual.get(1).parentCode()).isEqualTo("parent");
        assertThat(SourceFactDeduplication.merge(actual)).isEqualTo(actual);
    }

    @Test void unaryParentAndChildCanShareIdenticalSourceAndWeight() {
        var parent = fact("parent", "", "证书得5分", "证书得5分", "doc");
        var child = fact("child", "parent", "证书得5分", "证书得5分", "doc");
        assertThat(SourceFactDeduplication.merge(List.of(parent, child))).containsExactly(parent, child);
    }

    private static MultimodalScoreFact fact(String code, String parent, String rule, String quote, String document) {
        var spans = document.isBlank() ? List.<SourceSpan>of() : List.of(new SourceSpan(document, document+"-sha",
                "PDF", "scheme", 1, "table", "cell", 0, quote.length(), quote, "pdf:page=1"));
        return new MultimodalScoreFact("评分标准", code, parent, "评分项", new BigDecimal("5"), rule, "OBJECTIVE",
                1, "table", List.of("cell"), quote, .9, ChildAggregation.SUM, "scheme", "IN_SCHEME",
                new ScoreSourceMetadata(rule, quote, spans, List.of()));
    }
}
