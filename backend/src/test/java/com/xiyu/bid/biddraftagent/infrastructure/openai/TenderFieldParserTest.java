// Input: raw string values (budget, date) as LLM would produce
// Output: assertions on parsed Java types – verifies TenderFieldParser pure logic
// Pos: biddraftagent/infrastructure/openai (unit test)
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.biddraftagent.infrastructure.openai;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class TenderFieldParserTest {

    // ── parseBudget ────────────────────────────────────────────────────────────

    @Test
    void parseBudget_plainNumber_returnsExactValue() {
        assertThat(TenderFieldParser.parseBudget("6800000"))
                .isEqualTo(Optional.of(new BigDecimal("6800000")));
    }

    @Test
    void parseBudget_wanYuan_multipliesByTenThousand() {
        assertThat(TenderFieldParser.parseBudget("680万元"))
                .isEqualTo(Optional.of(new BigDecimal("6800000")));
    }

    @Test
    void parseBudget_wanOnly_multipliesByTenThousand() {
        assertThat(TenderFieldParser.parseBudget("680万"))
                .isEqualTo(Optional.of(new BigDecimal("6800000")));
    }

    @Test
    void parseBudget_yuanSuffix_returnsPlainValue() {
        assertThat(TenderFieldParser.parseBudget("6800000元"))
                .isEqualTo(Optional.of(new BigDecimal("6800000")));
    }

    @Test
    void parseBudget_renminbiPrefix_stripsPrefix() {
        assertThat(TenderFieldParser.parseBudget("人民币6800000元"))
                .isEqualTo(Optional.of(new BigDecimal("6800000")));
    }

    @Test
    void parseBudget_commaFormatted_stripsCommas() {
        assertThat(TenderFieldParser.parseBudget("6,800,000"))
                .isEqualTo(Optional.of(new BigDecimal("6800000")));
    }

    @Test
    void parseBudget_yueKeyword_returnsEmpty() {
        assertThat(TenderFieldParser.parseBudget("约680万")).isEmpty();
    }

    @Test
    void parseBudget_yujiKeyword_returnsEmpty() {
        assertThat(TenderFieldParser.parseBudget("预计680万元")).isEmpty();
    }

    @Test
    void parseBudget_null_returnsEmpty() {
        assertThat(TenderFieldParser.parseBudget(null)).isEmpty();
    }

    @Test
    void parseBudget_blank_returnsEmpty() {
        assertThat(TenderFieldParser.parseBudget("   ")).isEmpty();
    }

    // ── parsePublishDate ───────────────────────────────────────────────────────

    @Test
    void parsePublishDate_validIso_returnsLocalDate() {
        assertThat(TenderFieldParser.parsePublishDate("2024-01-15"))
                .isEqualTo(Optional.of(LocalDate.of(2024, 1, 15)));
    }

    @Test
    void parsePublishDate_invalid_returnsEmpty() {
        assertThat(TenderFieldParser.parsePublishDate("not-a-date")).isEmpty();
    }

    @Test
    void parsePublishDate_null_returnsEmpty() {
        assertThat(TenderFieldParser.parsePublishDate(null)).isEmpty();
    }

    // ── parseDeadline ──────────────────────────────────────────────────────────

    @Test
    void parseDeadline_fullDatetime_returnsLocalDateTime() {
        assertThat(TenderFieldParser.parseDeadline("2024-03-20T17:00:00"))
                .isEqualTo(Optional.of(LocalDateTime.of(2024, 3, 20, 17, 0, 0)));
    }

    @Test
    void parseDeadline_dateOnly_defaultsTo235959() {
        assertThat(TenderFieldParser.parseDeadline("2024-03-20"))
                .isEqualTo(Optional.of(LocalDateTime.of(2024, 3, 20, 23, 59, 59)));
    }

    @Test
    void parseDeadline_null_returnsEmpty() {
        assertThat(TenderFieldParser.parseDeadline(null)).isEmpty();
    }

    @Test
    void parseDeadline_invalid_returnsEmpty() {
        assertThat(TenderFieldParser.parseDeadline("garbage")).isEmpty();
    }
}
