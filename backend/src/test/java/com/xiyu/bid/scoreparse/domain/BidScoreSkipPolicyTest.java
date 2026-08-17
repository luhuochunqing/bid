package com.xiyu.bid.scoreparse.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BidScoreSkipPolicyTest {

    @Test
    void skipsOnlyWhenBothHashesMatch() {
        assertThat(BidScoreSkipPolicy.shouldSkip("aa", "bb", "aa", "bb")).isTrue();
        assertThat(BidScoreSkipPolicy.shouldSkip("aa", "bb", "aa", "cc")).isFalse();
        assertThat(BidScoreSkipPolicy.shouldSkip("aa", "bb", null, "bb")).isFalse();
    }

    @Test
    void hashesAreStable() {
        String first = BidScoreSkipPolicy.hashBytes("hello".getBytes());
        String second = BidScoreSkipPolicy.hashItems(List.of(
                BidScoreSkipPolicy.itemFingerprint(1L, 10, "资质")));
        assertThat(first).hasSize(64);
        assertThat(first).isEqualTo(BidScoreSkipPolicy.hashBytes("hello".getBytes()));
        assertThat(second).isNotEqualTo(first);
    }
}
