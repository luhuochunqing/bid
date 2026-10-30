package com.xiyu.bid.scoreparse.domain;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class PrimaryScoreFactPolicyTest {
    @Test void usesChildrenOnceWithoutGeometry() {
        var facts = List.of(fact("5", "", "10", ChildAggregation.SUM),
                fact("5.1", "5", "6", ChildAggregation.UNKNOWN),
                fact("5.2", "5", "4", ChildAggregation.UNKNOWN));
        var result = PrimaryScoreFactPolicy.candidates(facts, "规则5满分10分；规则5.1满分6分；规则5.2满分4分");
        assertThat(result).extracting(ScoreCandidate::code).containsExactly("5.1", "5.2");
        assertThat(result).extracting(ScoreCandidate::weight)
                .containsExactly(new BigDecimal("6"), new BigDecimal("4"));
    }
    @Test void duplicateParentsCannotBecomeSeparateRootContributions() {
        var parent = fact("5", "", "10", ChildAggregation.SUM);
        assertThat(PrimaryScoreFactPolicy.candidates(List.of(parent, parent,
                fact("5.1", "5", "10", ChildAggregation.UNKNOWN)), "规则5满分10分；规则5.1满分10分")).isEmpty();
    }
    @Test void ambiguousOrIncompleteChildSumCannotPassOnLeafTotalAlone() {
        assertThat(PrimaryScoreFactPolicy.candidates(List.of(fact("5", "", "20", ChildAggregation.SUM),
                fact("5.1", "5", "10", ChildAggregation.UNKNOWN)), "规则5满分10分；规则5.1满分10分")).isEmpty();
    }
    @Test void missingAndUnknownParentsCannotPublish() {
        assertThat(PrimaryScoreFactPolicy.candidates(List.of(fact("5.1", "5", "10", ChildAggregation.UNKNOWN)), "规则5.1满分10分")).isEmpty();
        assertThat(PrimaryScoreFactPolicy.candidates(List.of(fact("5", "", "10", ChildAggregation.UNKNOWN),
                fact("5.1", "5", "10", ChildAggregation.UNKNOWN)), "规则5满分10分；规则5.1满分10分")).isEmpty();
    }
    @Test void swappedWeightsCannotPassJustBecauseSumStillEqualsOneHundred() {
        var a = new MultimodalScoreFact("评分", "1", "", "甲", new BigDecimal("60"),
                "甲项满分40分。", "OBJECTIVE", 1, "", List.of(), "甲项满分40分。", 1);
        var b = new MultimodalScoreFact("评分", "2", "", "乙", new BigDecimal("40"),
                "乙项满分60分。", "OBJECTIVE", 1, "", List.of(), "乙项满分60分。", 1);
        assertThat(PrimaryScoreFactPolicy.candidates(List.of(a, b), "甲项满分40分。乙项满分60分。")).isEmpty();
    }
    @Test void renamedOrdinalCannotDuplicateOneSourceRule() {
        var a = new MultimodalScoreFact("评分", "1", "", "甲", new BigDecimal("50"),
                "甲项满分50分。", "OBJECTIVE", 1, "", List.of(), "甲项满分50分。", 1);
        var b = new MultimodalScoreFact("评分", "3", "", "甲", new BigDecimal("50"),
                "甲项满分50分。", "OBJECTIVE", 1, "", List.of(), "甲项满分50分。", 1);
        assertThat(PrimaryScoreFactPolicy.candidates(List.of(a, b), "甲项满分50分。乙项满分50分。")).isEmpty();
    }
    @Test void overlappingQuotesCannotCountOneSourceTwice() {
        var a = new MultimodalScoreFact("评分", "1", "", "甲", new BigDecimal("50"),
                "甲项满分50分。", "OBJECTIVE", 1, "", List.of(), "", 1);
        var b = new MultimodalScoreFact("评分", "2", "", "甲", new BigDecimal("50"),
                "甲项满分50分", "OBJECTIVE", 1, "", List.of(), "", 1);
        assertThat(PrimaryScoreFactPolicy.candidates(List.of(a, b), "甲项满分50分。乙项满分50分。")).isEmpty();
    }
    @Test void swappedNamesCannotBePublishedAgainstAnotherItemsRule() {
        var a = new MultimodalScoreFact("评分", "1", "", "业绩", new BigDecimal("40"),
                "资质满分40分。", "OBJECTIVE", 1, "", List.of(), "", 1);
        var b = new MultimodalScoreFact("评分", "2", "", "资质", new BigDecimal("60"),
                "业绩满分60分。", "OBJECTIVE", 1, "", List.of(), "", 1);
        assertThat(PrimaryScoreFactPolicy.candidates(List.of(a, b), "资质满分40分。业绩满分60分。")).isEmpty();
    }
    @Test void literalFirstSentenceDoesNotProveCompleteRule() {
        var cut = new MultimodalScoreFact("评分", "1", "", "技术方案", new BigDecimal("100"),
                "技术方案满分100分。", "SUBJECTIVE", 1, "", List.of(), "", 1);
        assertThat(PrimaryScoreFactPolicy.candidates(List.of(cut),
                "技术方案满分100分。必须提供A证明，未提供不得分。")).isEmpty();
    }
    private static MultimodalScoreFact fact(String code, String parent, String weight, ChildAggregation relation) {
        return new MultimodalScoreFact("评分", code, parent, "规则" + code, new BigDecimal(weight),
                "规则" + code + "满分" + weight + "分", "OBJECTIVE", 1, "", List.of(), "规则" + code, 1, relation);
    }
}
