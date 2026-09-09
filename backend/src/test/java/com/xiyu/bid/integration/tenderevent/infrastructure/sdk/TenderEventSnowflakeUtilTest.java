package com.xiyu.bid.integration.tenderevent.infrastructure.sdk;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 标讯事件链路 ID 生成器契约测试。
 *
 * <p>客户事件库 trace_id / span_id 字段限 18 位数字，仅接受纯数字、无前缀（历史正误：
 * t/s+18位=19字符 超长被拒）。本测试是 CO 标讯推送链路回归护栏。
 */
class TenderEventSnowflakeUtilTest {

    private static final int MAX_LENGTH = 18;

    @Test
    void traceIdAndSpanIdShouldBePureDigitsWithin18() {
        for (int i = 0; i < 1000; i++) {
            String traceId = TenderEventSnowflakeUtil.nextTraceId();
            String spanId = TenderEventSnowflakeUtil.nextSpanId();

            assertThat(traceId).matches("\\d{1," + MAX_LENGTH + "}");
            assertThat(spanId).matches("\\d{1," + MAX_LENGTH + "}");
            assertThat(traceId.length()).isLessThanOrEqualTo(MAX_LENGTH);
            assertThat(spanId.length()).isLessThanOrEqualTo(MAX_LENGTH);
        }
    }

    @Test
    void consecutiveIdsShouldBeUnique() {
        String first = TenderEventSnowflakeUtil.nextTraceId();
        String second = TenderEventSnowflakeUtil.nextTraceId();
        assertThat(first).isNotEqualTo(second);
    }
}