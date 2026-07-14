package com.xiyu.bid.tender.core;

import com.xiyu.bid.entity.Tender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TenderDeduplicationPolicy - 标讯去重策略")
class TenderDeduplicationPolicyTest {

    private static final String PURCHASER_A = "测试招标主体";
    private static final String PURCHASER_B = "另一个招标主体";
    private static final String PROJECT_TYPE_X = "工程类";
    private static final String PROJECT_TYPE_Y = "服务类";
    private static final LocalDateTime REG_DEADLINE = LocalDateTime.of(2026, 6, 30, 17, 0);
    private static final LocalDateTime BID_OPEN_TIME = LocalDateTime.of(2026, 7, 15, 10, 0);

    @Nested
    @DisplayName("完全匹配场景")
    class ExactMatch {

        @Test
        @DisplayName("四字段完全匹配时应判定为重复")
        void shouldDetectDuplicate_whenAllFieldsMatch() {
            assertThat(TenderDeduplicationPolicy.isDuplicate(
                    PURCHASER_A, PROJECT_TYPE_X, REG_DEADLINE, BID_OPEN_TIME,
                    PURCHASER_A, PROJECT_TYPE_X, REG_DEADLINE, BID_OPEN_TIME
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
                    PURCHASER_A, PROJECT_TYPE_X, REG_DEADLINE, BID_OPEN_TIME,
                    PURCHASER_B, PROJECT_TYPE_X, REG_DEADLINE, BID_OPEN_TIME
            )).isFalse();
        }

        @Test
        @DisplayName("项目类型不同时应判定为不重复")
        void shouldNotDetectDuplicate_whenProjectTypeDiffers() {
            assertThat(TenderDeduplicationPolicy.isDuplicate(
                    PURCHASER_A, PROJECT_TYPE_X, REG_DEADLINE, BID_OPEN_TIME,
                    PURCHASER_A, PROJECT_TYPE_Y, REG_DEADLINE, BID_OPEN_TIME
            )).isFalse();
        }

        @Test
        @DisplayName("报名截止时间不同时应判定为不重复")
        void shouldNotDetectDuplicate_whenRegDeadlineDiffers() {
            LocalDateTime otherRegDeadline = REG_DEADLINE.plusDays(1);
            assertThat(TenderDeduplicationPolicy.isDuplicate(
                    PURCHASER_A, PROJECT_TYPE_X, REG_DEADLINE, BID_OPEN_TIME,
                    PURCHASER_A, PROJECT_TYPE_X, otherRegDeadline, BID_OPEN_TIME
            )).isFalse();
        }

        @Test
        @DisplayName("开标时间不同时应判定为不重复")
        void shouldNotDetectDuplicate_whenBidOpenTimeDiffers() {
            LocalDateTime otherBidOpenTime = BID_OPEN_TIME.plusDays(1);
            assertThat(TenderDeduplicationPolicy.isDuplicate(
                    PURCHASER_A, PROJECT_TYPE_X, REG_DEADLINE, BID_OPEN_TIME,
                    PURCHASER_A, PROJECT_TYPE_X, REG_DEADLINE, otherBidOpenTime
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
                    null, PROJECT_TYPE_X, REG_DEADLINE, BID_OPEN_TIME,
                    null, PROJECT_TYPE_X, REG_DEADLINE, BID_OPEN_TIME
            )).isFalse();
        }

        @Test
        @DisplayName("报名截止时间为 null 时应判定为不重复")
        void shouldNotDetectDuplicate_whenRegDeadlineIsNull() {
            assertThat(TenderDeduplicationPolicy.isDuplicate(
                    PURCHASER_A, PROJECT_TYPE_X, null, BID_OPEN_TIME,
                    PURCHASER_A, PROJECT_TYPE_X, null, BID_OPEN_TIME
            )).isFalse();
        }

        @Test
        @DisplayName("开标时间为 null 时应判定为不重复")
        void shouldNotDetectDuplicate_whenBidOpenTimeIsNull() {
            assertThat(TenderDeduplicationPolicy.isDuplicate(
                    PURCHASER_A, PROJECT_TYPE_X, REG_DEADLINE, null,
                    PURCHASER_A, PROJECT_TYPE_X, REG_DEADLINE, null
            )).isFalse();
        }

        @Test
        @DisplayName("招标主体为空字符串时应判定为不重复")
        void shouldNotDetectDuplicate_whenPurchaserIsBlank() {
            assertThat(TenderDeduplicationPolicy.isDuplicate(
                    "  ", PROJECT_TYPE_X, REG_DEADLINE, BID_OPEN_TIME,
                    "  ", PROJECT_TYPE_X, REG_DEADLINE, BID_OPEN_TIME
            )).isFalse();
        }

        @Test
        @DisplayName("项目类型双方均为 null 时应判定为重复（null 归一化为空字符串后匹配）")
        void shouldDetectDuplicate_whenBothProjectTypesAreNull() {
            assertThat(TenderDeduplicationPolicy.isDuplicate(
                    PURCHASER_A, null, REG_DEADLINE, BID_OPEN_TIME,
                    PURCHASER_A, null, REG_DEADLINE, BID_OPEN_TIME
            )).isTrue();
        }

        @Test
        @DisplayName("项目类型一方为 null 一方有值时应判定为不重复")
        void shouldNotDetectDuplicate_whenOneProjectTypeIsNullAndOtherHasValue() {
            assertThat(TenderDeduplicationPolicy.isDuplicate(
                    PURCHASER_A, null, REG_DEADLINE, BID_OPEN_TIME,
                    PURCHASER_A, PROJECT_TYPE_X, REG_DEADLINE, BID_OPEN_TIME
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
                    "测试招标主体", PROJECT_TYPE_X, REG_DEADLINE, BID_OPEN_TIME,
                    "测试招标主体", PROJECT_TYPE_X, REG_DEADLINE, BID_OPEN_TIME
            )).isTrue();
        }

        @Test
        @DisplayName("招标主体前后空格不同时应判定为重复（trim 后匹配）")
        void shouldDetectDuplicate_whenPurchaserHasLeadingTrailingSpaces() {
            assertThat(TenderDeduplicationPolicy.isDuplicate(
                    "  测试招标主体  ", PROJECT_TYPE_X, REG_DEADLINE, BID_OPEN_TIME,
                    "测试招标主体", PROJECT_TYPE_X, REG_DEADLINE, BID_OPEN_TIME
            )).isTrue();
        }

        @Test
        @DisplayName("项目类型前后空格不同时应判定为重复（trim 后匹配）")
        void shouldDetectDuplicate_whenProjectTypeHasLeadingTrailingSpaces() {
            assertThat(TenderDeduplicationPolicy.isDuplicate(
                    PURCHASER_A, "  工程类  ", REG_DEADLINE, BID_OPEN_TIME,
                    PURCHASER_A, "工程类", REG_DEADLINE, BID_OPEN_TIME
            )).isTrue();
        }

        @Test
        @DisplayName("项目类型大小写不同时应判定为重复（忽略大小写）")
        void shouldDetectDuplicate_whenProjectTypeCaseDiffers() {
            assertThat(TenderDeduplicationPolicy.isDuplicate(
                    PURCHASER_A, "Engineering", REG_DEADLINE, BID_OPEN_TIME,
                    PURCHASER_A, "ENGINEERING", REG_DEADLINE, BID_OPEN_TIME
            )).isTrue();
        }
    }

    @Nested
    @DisplayName("时间精度场景")
    class TimePrecision {

        @Test
        @DisplayName("报名截止时间秒以下精度不同应判定为重复")
        void shouldDetectDuplicate_whenRegDeadlineSubSecondDiffers() {
            LocalDateTime withMillis = REG_DEADLINE.plusNanos(123_000_000);
            assertThat(TenderDeduplicationPolicy.isDuplicate(
                    PURCHASER_A, PROJECT_TYPE_X, REG_DEADLINE, BID_OPEN_TIME,
                    PURCHASER_A, PROJECT_TYPE_X, withMillis, BID_OPEN_TIME
            )).isTrue();
        }

        @Test
        @DisplayName("开标时间秒以下精度不同应判定为重复")
        void shouldDetectDuplicate_whenBidOpenTimeSubSecondDiffers() {
            LocalDateTime withMillis = BID_OPEN_TIME.plusNanos(999_000_000);
            assertThat(TenderDeduplicationPolicy.isDuplicate(
                    PURCHASER_A, PROJECT_TYPE_X, REG_DEADLINE, BID_OPEN_TIME,
                    PURCHASER_A, PROJECT_TYPE_X, REG_DEADLINE, withMillis
            )).isTrue();
        }

        @Test
        @DisplayName("报名截止时间秒级差异仍应判定为不重复")
        void shouldNotDetectDuplicate_whenRegDeadlineSecondDiffers() {
            LocalDateTime oneSecondLater = REG_DEADLINE.plusSeconds(1);
            assertThat(TenderDeduplicationPolicy.isDuplicate(
                    PURCHASER_A, PROJECT_TYPE_X, REG_DEADLINE, BID_OPEN_TIME,
                    PURCHASER_A, PROJECT_TYPE_X, oneSecondLater, BID_OPEN_TIME
            )).isFalse();
        }
    }

    // ---- formatDuplicateMessage 测试 ----

    @Nested
    @DisplayName("formatDuplicateMessage - 基本格式")
    class FormatDuplicateMessage {

        @Test
        @DisplayName("应生成包含四字段的重复提示")
        void shouldFormatMessageWithAllFields() {
            String msg = TenderDeduplicationPolicy.formatDuplicateMessage(
                    PURCHASER_A, PROJECT_TYPE_X, REG_DEADLINE, BID_OPEN_TIME);

            assertThat(msg).contains(PURCHASER_A);
            assertThat(msg).contains(PROJECT_TYPE_X);
            assertThat(msg).contains("2026-06-30");
            assertThat(msg).contains("2026-07-15");
            assertThat(msg).contains("已存在");
        }
    }

    // ---- formatImportDuplicateMessage 测试 ----
    // 覆盖 commit 03592c754 的修改：existing != null 时取 existing.getPurchaserName() 而非 newRowPurchaser

    @Nested
    @DisplayName("formatImportDuplicateMessage - 批量导入去重提示")
    class FormatImportDuplicateMessage {

        @Test
        @DisplayName("existing != null 时应使用已有标讯的标题和招标主体")
        void shouldUseExistingTitleAndPurchaser_whenExistingNotNull() {
            Tender existing = Tender.builder()
                    .id(1L)
                    .title("测试项目标讯")
                    .purchaserName("已有采购方")
                    .build();

            String msg = TenderDeduplicationPolicy.formatImportDuplicateMessage(existing, "新采购方");

            assertThat(msg).contains("测试项目标讯");
            assertThat(msg).contains("已有采购方"); // 使用 existing.getPurchaserName()，而非 newRowPurchaser
            assertThat(msg).contains("标讯重复");
        }

        @Test
        @DisplayName("existing != null 但标题为 null 时应显示无标题占位符")
        void shouldShowNoTitlePlaceholder_whenExistingTitleIsNull() {
            Tender existing = Tender.builder()
                    .id(1L)
                    .title(null)
                    .purchaserName("已有采购方")
                    .build();

            String msg = TenderDeduplicationPolicy.formatImportDuplicateMessage(existing, "新采购方");

            assertThat(msg).contains("(无标题)");
            assertThat(msg).contains("已有采购方");
        }

        @Test
        @DisplayName("existing != null 但招标主体为 null 时应显示空字符串")
        void shouldShowEmptyPurchaser_whenExistingPurchaserIsNull() {
            Tender existing = Tender.builder()
                    .id(1L)
                    .title("测试标讯")
                    .purchaserName(null)
                    .build();

            String msg = TenderDeduplicationPolicy.formatImportDuplicateMessage(existing, "新采购方");

            assertThat(msg).contains("测试标讯");
            assertThat(msg).contains("标讯重复");
        }

        @Test
        @DisplayName("existing == null 时应使用 newRowPurchaser 并提示缺失详情")
        void shouldUseNewRowPurchaser_whenExistingIsNull() {
            String msg = TenderDeduplicationPolicy.formatImportDuplicateMessage(null, "新采购方");

            assertThat(msg).contains("新采购方");
            assertThat(msg).contains("标讯重复");
            assertThat(msg).contains("系统判定为同一条标讯");
        }

        @Test
        @DisplayName("existing == null 且 newRowPurchaser 也为 null 时应优雅处理")
        void shouldHandleNullPurchaser_whenBothExistingAndNewRowPurchaserAreNull() {
            String msg = TenderDeduplicationPolicy.formatImportDuplicateMessage(null, null);

            assertThat(msg).contains("标讯重复");
            assertThat(msg).doesNotContain("NullPointerException");
            assertThat(msg).doesNotContain("null"); // null 作为参数值不应出现在消息中
        }
    }
}
