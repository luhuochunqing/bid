package com.xiyu.bid.tender.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TenderDeduplicationPolicy - 标讯去重策略")
class TenderDeduplicationPolicyTest {

    private static final String PURCHASER_A = "测试招标主体";
    private static final String PURCHASER_B = "另一个招标主体";
    private static final LocalDateTime REG_DEADLINE = LocalDateTime.of(2026, 6, 30, 17, 0);
    private static final LocalDateTime BID_OPEN_TIME = LocalDateTime.of(2026, 7, 15, 10, 0);

    @Nested
    @DisplayName("完全匹配场景")
    class ExactMatch {

        @Test
        @DisplayName("三字段完全匹配时应判定为重复")
        void shouldDetectDuplicate_whenAllFieldsMatch() {
            assertThat(TenderDeduplicationPolicy.isDuplicate(
                    PURCHASER_A, REG_DEADLINE, BID_OPEN_TIME,
                    PURCHASER_A, REG_DEADLINE, BID_OPEN_TIME
            )).isTrue();
        }
    }

    @Nested
    @DisplayName("字段差异场景")
    class FieldDifference {

        @Test
        @DisplayName("招标主体不同时应判定为不重复")
        void shouldNotDetectDuplicate_whenPurchaserDiffers() {
            assertThat(TenderDeduplicationPolicy.isDuplicate(
                    PURCHASER_A, REG_DEADLINE, BID_OPEN_TIME,
                    PURCHASER_B, REG_DEADLINE, BID_OPEN_TIME
            )).isFalse();
        }

        @Test
        @DisplayName("报名截止时间不同时应判定为不重复")
        void shouldNotDetectDuplicate_whenRegDeadlineDiffers() {
            LocalDateTime otherRegDeadline = REG_DEADLINE.plusDays(1);
            assertThat(TenderDeduplicationPolicy.isDuplicate(
                    PURCHASER_A, REG_DEADLINE, BID_OPEN_TIME,
                    PURCHASER_A, otherRegDeadline, BID_OPEN_TIME
            )).isFalse();
        }

        @Test
        @DisplayName("开标时间不同时应判定为不重复")
        void shouldNotDetectDuplicate_whenBidOpenTimeDiffers() {
            LocalDateTime otherBidOpenTime = BID_OPEN_TIME.plusDays(1);
            assertThat(TenderDeduplicationPolicy.isDuplicate(
                    PURCHASER_A, REG_DEADLINE, BID_OPEN_TIME,
                    PURCHASER_A, REG_DEADLINE, otherBidOpenTime
            )).isFalse();
        }
    }

    @Nested
    @DisplayName("空值边界场景")
    class NullHandling {

        @Test
        @DisplayName("招标主体为 null 时应判定为不重复")
        void shouldNotDetectDuplicate_whenPurchaserIsNull() {
            assertThat(TenderDeduplicationPolicy.isDuplicate(
                    null, REG_DEADLINE, BID_OPEN_TIME,
                    null, REG_DEADLINE, BID_OPEN_TIME
            )).isFalse();
        }

        @Test
        @DisplayName("报名截止时间为 null 时应判定为不重复")
        void shouldNotDetectDuplicate_whenRegDeadlineIsNull() {
            assertThat(TenderDeduplicationPolicy.isDuplicate(
                    PURCHASER_A, null, BID_OPEN_TIME,
                    PURCHASER_A, null, BID_OPEN_TIME
            )).isFalse();
        }

        @Test
        @DisplayName("开标时间为 null 时应判定为不重复")
        void shouldNotDetectDuplicate_whenBidOpenTimeIsNull() {
            assertThat(TenderDeduplicationPolicy.isDuplicate(
                    PURCHASER_A, REG_DEADLINE, null,
                    PURCHASER_A, REG_DEADLINE, null
            )).isFalse();
        }

        @Test
        @DisplayName("招标主体为空字符串时应判定为不重复")
        void shouldNotDetectDuplicate_whenPurchaserIsBlank() {
            assertThat(TenderDeduplicationPolicy.isDuplicate(
                    "  ", REG_DEADLINE, BID_OPEN_TIME,
                    "  ", REG_DEADLINE, BID_OPEN_TIME
            )).isFalse();
        }
    }

    @Nested
    @DisplayName("大小写与空格场景")
    class Normalization {

        @Test
        @DisplayName("招标主体大小写不同时应判定为重复（忽略大小写）")
        void shouldDetectDuplicate_whenPurchaserCaseDiffers() {
            assertThat(TenderDeduplicationPolicy.isDuplicate(
                    "测试招标主体", REG_DEADLINE, BID_OPEN_TIME,
                    "测试招标主体", REG_DEADLINE, BID_OPEN_TIME
            )).isTrue();
        }

        @Test
        @DisplayName("招标主体前后空格不同时应判定为重复（trim 后匹配）")
        void shouldDetectDuplicate_whenPurchaserHasLeadingTrailingSpaces() {
            assertThat(TenderDeduplicationPolicy.isDuplicate(
                    "  测试招标主体  ", REG_DEADLINE, BID_OPEN_TIME,
                    "测试招标主体", REG_DEADLINE, BID_OPEN_TIME
            )).isTrue();
        }
    }
}
