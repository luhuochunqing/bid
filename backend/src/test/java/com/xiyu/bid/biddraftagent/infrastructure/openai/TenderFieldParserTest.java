// Input: raw string values (budget, date) as LLM would produce
// Output: assertions on parsed Java types – verifies TenderFieldParser pure logic
// Pos: biddraftagent/infrastructure/openai (unit test)
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.biddraftagent.infrastructure.openai;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class TenderFieldParserTest {

    // ── parseBudget ────────────────────────────────────────────────────────────

    @Test
    void parseBudget_plainNumber_returnsExactValue() {
        assertThat(TenderFieldParser.parseBudget("6800000"))
                .isEqualByComparingTo(new BigDecimal("6800000"));
    }

    @Test
    void parseBudget_wanYuan_multipliesByTenThousand() {
        assertThat(TenderFieldParser.parseBudget("680万元"))
                .isEqualByComparingTo(new BigDecimal("6800000"));
    }

    @Test
    void parseBudget_wanOnly_multipliesByTenThousand() {
        assertThat(TenderFieldParser.parseBudget("680万"))
                .isEqualByComparingTo(new BigDecimal("6800000"));
    }

    @Test
    void parseBudget_yuanSuffix_returnsPlainValue() {
        assertThat(TenderFieldParser.parseBudget("6800000元"))
                .isEqualByComparingTo(new BigDecimal("6800000"));
    }

    @Test
    void parseBudget_renminbiPrefix_stripsPrefix() {
        assertThat(TenderFieldParser.parseBudget("人民币6800000元"))
                .isEqualByComparingTo(new BigDecimal("6800000"));
    }

    @Test
    void parseBudget_commaFormatted_stripsCommas() {
        assertThat(TenderFieldParser.parseBudget("6,800,000"))
                .isEqualByComparingTo(new BigDecimal("6800000"));
    }

    @Test
    void parseBudget_yueKeyword_returnsNull() {
        assertThat(TenderFieldParser.parseBudget("约680万")).isNull();
    }

    @Test
    void parseBudget_yujiKeyword_returnsNull() {
        assertThat(TenderFieldParser.parseBudget("预计680万元")).isNull();
    }

    @Test
    void parseBudget_null_returnsNull() {
        assertThat(TenderFieldParser.parseBudget(null)).isNull();
    }

    @Test
    void parseBudget_blank_returnsNull() {
        assertThat(TenderFieldParser.parseBudget("   ")).isNull();
    }

    // ── parsePublishDate ───────────────────────────────────────────────────────

    @Test
    void parsePublishDate_validIso_returnsLocalDate() {
        assertThat(TenderFieldParser.parsePublishDate("2024-01-15"))
                .isEqualTo(LocalDate.of(2024, 1, 15));
    }

    @Test
    void parsePublishDate_invalid_returnsNull() {
        assertThat(TenderFieldParser.parsePublishDate("not-a-date")).isNull();
    }

    @Test
    void parsePublishDate_null_returnsNull() {
        assertThat(TenderFieldParser.parsePublishDate(null)).isNull();
    }

    // ── parseDeadline ──────────────────────────────────────────────────────────

    @Test
    void parseDeadline_fullDatetime_returnsLocalDateTime() {
        assertThat(TenderFieldParser.parseDeadline("2024-03-20T17:00:00"))
                .isEqualTo(LocalDateTime.of(2024, 3, 20, 17, 0, 0));
    }

    @Test
    void parseDeadline_dateOnly_defaultsTo235959() {
        assertThat(TenderFieldParser.parseDeadline("2024-03-20"))
                .isEqualTo(LocalDateTime.of(2024, 3, 20, 23, 59, 59));
    }

    @Test
    void parseDeadline_null_returnsNull() {
        assertThat(TenderFieldParser.parseDeadline(null)).isNull();
    }

    @Test
    void parseDeadline_invalid_returnsNull() {
        assertThat(TenderFieldParser.parseDeadline("garbage")).isNull();
    }
}
