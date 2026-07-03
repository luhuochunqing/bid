package com.xiyu.bid.warehouse.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class WarehouseDateParserTest {

    @ParameterizedTest
    @CsvSource({
            "2026-07-03, 2026, 7, 3",
            "2026/07/03, 2026, 7, 3",
            "2026.07.03, 2026, 7, 3",
            "2026年7月3日, 2026, 7, 3",
            "03-07-2026, 2026, 7, 3",
            "03/07/2026, 2026, 7, 3",
            "2026-1-1, 2026, 1, 1",
            "2026/1/1, 2026, 1, 1",
            "2026.1.1, 2026, 1, 1",
            "2026年1月1日, 2026, 1, 1"
    })
    @DisplayName("parse 支持 ISO、slash、dot、中文、DMY 等多种日期格式")
    void parseSupportsCommonDateFormats(String text, int year, int month, int day) {
        LocalDate date = WarehouseDateParser.parse(text);
        assertThat(date).isNotNull();
        assertThat(date.getYear()).isEqualTo(year);
        assertThat(date.getMonthValue()).isEqualTo(month);
        assertThat(date.getDayOfMonth()).isEqualTo(day);
    }

    @Test
    @DisplayName("parse 对非法格式返回 null")
    void parseReturnsNullForInvalidFormat() {
        assertThat(WarehouseDateParser.parse("not-a-date")).isNull();
        assertThat(WarehouseDateParser.parse("2026-13-01")).isNull();
        assertThat(WarehouseDateParser.parse("2026/07/32")).isNull();
    }

    @Test
    @DisplayName("parse 对空值与空白返回 null")
    void parseReturnsNullForBlankInput() {
        assertThat(WarehouseDateParser.parse(null)).isNull();
        assertThat(WarehouseDateParser.parse("")).isNull();
        assertThat(WarehouseDateParser.parse("   ")).isNull();
    }

    @Test
    @DisplayName("parse 会去除前后空白")
    void parseTrimsInput() {
        LocalDate date = WarehouseDateParser.parse("  2026-07-03  ");
        assertThat(date).isEqualTo(LocalDate.of(2026, 7, 3));
    }
}
