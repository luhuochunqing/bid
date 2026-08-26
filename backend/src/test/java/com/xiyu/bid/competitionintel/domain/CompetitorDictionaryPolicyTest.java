package com.xiyu.bid.competitionintel.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CompetitorDictionaryPolicyTest {

    @Test
    void normalizeName_WithNull_ReturnsNull() {
        assertThat(CompetitorDictionaryPolicy.normalizeName(null)).isNull();
    }

    @Test
    void normalizeName_WithBlank_ReturnsNull() {
        assertThat(CompetitorDictionaryPolicy.normalizeName("   ")).isNull();
        assertThat(CompetitorDictionaryPolicy.normalizeName("")).isNull();
    }

    @Test
    void normalizeName_WithSurroundingWhitespace_Trims() {
        assertThat(CompetitorDictionaryPolicy.normalizeName("  震坤行  ")).isEqualTo("震坤行");
        assertThat(CompetitorDictionaryPolicy.normalizeName("震坤行")).isEqualTo("震坤行");
    }

    @Test
    void decideMerge_WithNullIds_Denies() {
        CompetitorDictionaryPolicy.Decision d1 = CompetitorDictionaryPolicy.decideMerge(null, 2L);
        CompetitorDictionaryPolicy.Decision d2 = CompetitorDictionaryPolicy.decideMerge(1L, null);
        assertThat(d1.allowed()).isFalse();
        assertThat(d2.allowed()).isFalse();
    }

    @Test
    void decideMerge_WithSameSourceAndTarget_Denies() {
        CompetitorDictionaryPolicy.Decision d = CompetitorDictionaryPolicy.decideMerge(5L, 5L);
        assertThat(d.allowed()).isFalse();
        assertThat(d.reason()).contains("自身");
    }

    @Test
    void decideMerge_WithDistinctIds_Allows() {
        CompetitorDictionaryPolicy.Decision d = CompetitorDictionaryPolicy.decideMerge(1L, 2L);
        assertThat(d.allowed()).isTrue();
    }
}
