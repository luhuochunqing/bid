package com.xiyu.bid.wecom;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("WecomSendResult — 工厂构造正确")
class WecomSendResultTest {

    @Test
    @DisplayName("success 工厂置位 success=true")
    void success_factory() {
        WecomSendResult r = WecomSendResult.success(0, "ok");

        assertThat(r.success()).isTrue();
        assertThat(r.code()).isEqualTo(0);
        assertThat(r.message()).isEqualTo("ok");
    }

    @Test
    @DisplayName("failure 工厂置位 success=false")
    void failure_factory() {
        WecomSendResult r = WecomSendResult.failure(500, "boom");

        assertThat(r.success()).isFalse();
        assertThat(r.code()).isEqualTo(500);
        assertThat(r.message()).isEqualTo("boom");
    }
}
