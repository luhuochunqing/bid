package com.xiyu.bid.scoreparse.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ScoreUnitPolicyTest {

    @Test
    void usesOnlyEffectiveUnitsAcrossAdditiveAndNonAdditiveParents() {
        var facts = List.of(
                node("sum-root", "", "35", ChildAggregation.SUM),
                node("shared", "sum-root", "5", ChildAggregation.SHARED_CAP),
                node("condition-a", "shared", "3", ChildAggregation.SUM),
                node("condition-b", "shared", "4", ChildAggregation.SUM),
                node("additive", "sum-root", "30", ChildAggregation.SUM),
                node("child-a", "additive", "10", ChildAggregation.SUM),
                node("child-b", "additive", "20", ChildAggregation.SUM),
                node("best", "", "3", ChildAggregation.BEST_OF),
                node("best-a", "best", "2", ChildAggregation.SUM),
                node("best-b", "best", "3", ChildAggregation.SUM),
                node("exclusive", "", "2", ChildAggregation.MUTUALLY_EXCLUSIVE),
                node("exclusive-a", "exclusive", "1", ChildAggregation.SUM),
                node("exclusive-b", "exclusive", "2", ChildAggregation.SUM));

        var units = ScoreUnitPolicy.effectiveUnitIds(facts);

        assertThat(units).containsExactlyInAnyOrder("shared", "child-a", "child-b", "best", "exclusive");
        assertThat(ScoreUnitPolicy.totalWeight(facts)).isEqualByComparingTo("40");
        assertThat(ScoreUnitPolicy.hasUnknownRelation(facts)).isFalse();
    }

    @Test
    void unknownRelationshipIsReportedAndNeverExpandedAsAdditive() {
        var facts = List.of(node("root", "", "10", ChildAggregation.UNKNOWN),
                node("a", "root", "6", ChildAggregation.SUM), node("b", "root", "4", ChildAggregation.SUM));

        assertThat(ScoreUnitPolicy.effectiveUnitIds(facts)).isEmpty();
        assertThat(ScoreUnitPolicy.hasUnknownRelation(facts)).isTrue();
    }

    @Test
    void exclusiveFiveFourThreeTwoTiersCountTheirFivePointParentWhileSumChildrenStillTotalTwenty() {
        var facts = List.of(
                node("tier-parent", "", "5", ChildAggregation.MUTUALLY_EXCLUSIVE),
                node("tier-5", "tier-parent", "5", ChildAggregation.SUM),
                node("tier-4", "tier-parent", "4", ChildAggregation.SUM),
                node("tier-3", "tier-parent", "3", ChildAggregation.SUM),
                node("tier-2", "tier-parent", "2", ChildAggregation.SUM),
                node("sum-parent", "", "20", ChildAggregation.SUM),
                node("sum-5-a", "sum-parent", "5", ChildAggregation.SUM),
                node("sum-5-b", "sum-parent", "5", ChildAggregation.SUM),
                node("sum-10", "sum-parent", "10", ChildAggregation.SUM));

        assertThat(ScoreUnitPolicy.effectiveUnitIds(facts)).containsExactlyInAnyOrder(
                "tier-parent", "sum-5-a", "sum-5-b", "sum-10");
        assertThat(ScoreUnitPolicy.totalWeight(facts)).isEqualByComparingTo("25");
    }

    private static ScoreUnitPolicy.Node node(String id, String parent, String weight, ChildAggregation relation) {
        return new ScoreUnitPolicy.Node(id, parent, new BigDecimal(weight), relation.name());
    }
}
