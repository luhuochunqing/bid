package com.xiyu.bid.scoreparse.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AutoParseGateTest {

    @Test
    void allowsOnlyWhenNoHistoryAndNoItems() {
        assertThat(AutoParseGate.allowAutoCreate(false, false)).isTrue();
        assertThat(AutoParseGate.allowAutoCreate(true, false)).isFalse();
        assertThat(AutoParseGate.allowAutoCreate(false, true)).isFalse();
        assertThat(AutoParseGate.allowAutoCreate(true, true)).isFalse();
    }
}
