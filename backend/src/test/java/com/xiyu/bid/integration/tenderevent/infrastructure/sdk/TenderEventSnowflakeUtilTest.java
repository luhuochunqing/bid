package com.xiyu.bid.integration.tenderevent.infrastructure.sdk;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 标讯事件链路 ID 生成器契约测试。
 *
 * <p>生产 Pod 使用客户 SDK Snowflake；无 HOSTNAME 的本地环境使用 87 位安全随机数。
 * 本测试只验证适配格式、长度和样本内不重复，不证明跨节点全局唯一性。
 */
class TenderEventSnowflakeUtilTest {

    private static final int MAX_LENGTH = 18;

    @Test
    void traceIdAndSpanIdShouldHaveTypedBase36PrefixWithin18() {
        for (int i = 0; i < 1000; i++) {
            String traceId = TenderEventSnowflakeUtil.nextTraceId();
            String spanId = TenderEventSnowflakeUtil.nextSpanId();

            assertThat(traceId).matches("^t[0-9a-z]+$");
            assertThat(spanId).matches("^s[0-9a-z]+$");
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

    @Test
    void unsignedLongEncodingShouldBeLosslessAndWithin13Characters() {
        assertThat(TenderEventSnowflakeUtil.encode(-1L)).isEqualTo("3w5e11264sgsf");
        assertThat(TenderEventSnowflakeUtil.encode(-1L)).hasSizeLessThanOrEqualTo(13);
    }
}
