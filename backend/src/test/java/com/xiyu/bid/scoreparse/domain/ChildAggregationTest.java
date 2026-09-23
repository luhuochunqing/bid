package com.xiyu.bid.scoreparse.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChildAggregationTest {
    @Test
    void additiveRelationIsRejectedWhenOriginalTextStatesASharedOrNonAdditiveRule() {
        assertThat(ChildAggregation.SUM.supportedByOriginalText("三个条件共享最高5分，满足任一条件即可"))
                .isFalse();
        assertThat(ChildAggregation.SUM.supportedByOriginalText("三项分别计分，分值累加"))
                .isTrue();
        assertThat(ChildAggregation.SHARED_CAP.supportedByOriginalText("三个条件共享最高5分"))
                .isTrue();
        assertThat(ChildAggregation.BEST_OF.supportedByOriginalText("按最高分者计取"))
                .isTrue();
        assertThat(ChildAggregation.MUTUALLY_EXCLUSIVE.supportedByOriginalText("方案任选其一"))
                .isTrue();
    }
}
