package com.xiyu.bid.common.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class CommonDateParserTest {

    @Test
    void parseDayPrecision_null_returnsNull() {
        assertNull(CommonDateParser.parseDayPrecision(null));
    }

    @Test
    void parseDayPrecision_blank_returnsNull() {
        assertNull(CommonDateParser.parseDayPrecision("   "));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2024-01-15", "2024-1-15", "2024/01/15", "2024/1/15",
            "2024.01.15", "2024.1.15", "2024年1月15日", "2024年01月15日",
            "15-1-2024", "15/1/2024", "01/15/2024", "15.01.2024"})
    void parseDayPrecision_variousFormats_returnsCorrectDate(String input) {
        LocalDate result = CommonDateParser.parseDayPrecision(input);
        assertNotNull(result);
        assertEquals(2024, result.getYear());
        assertEquals(1, result.getMonthValue());
        assertEquals(15, result.getDayOfMonth());
    }

    @Test
    void parseDayPrecision_invalidFormat_returnsNull() {
        assertNull(CommonDateParser.parseDayPrecision("invalid-date"));
    }

    @Test
    void parseDayPrecisionOrThrow_valid_returnsDate() {
        LocalDate result = CommonDateParser.parseDayPrecisionOrThrow("2024-01-15", "测试日期");
        assertNotNull(result);
        assertEquals(2024, result.getYear());
    }

    @Test
    void parseDayPrecisionOrThrow_invalid_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> CommonDateParser.parseDayPrecisionOrThrow("invalid", "测试日期"));
    }

    @Test
    void parseDateTimePrecision_null_returnsNull() {
        assertNull(CommonDateParser.parseDateTimePrecision(null));
    }

    @Test
    void parseDateTimePrecision_blank_returnsNull() {
        assertNull(CommonDateParser.parseDateTimePrecision("   "));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2024-01-15 17:00:00", "2024-01-15 17:00",
            "2024/01/15 17:00:00", "2024/01/15 17:00",
            "2024年1月15日 17:00", "2024年1月15日 17:00:00"})
    void parseDateTimePrecision_variousDateTimeFormats_returnsCorrectDateTime(String input) {
        LocalDateTime result = CommonDateParser.parseDateTimePrecision(input);
        assertNotNull(result);
        assertEquals(2024, result.getYear());
        assertEquals(1, result.getMonthValue());
        assertEquals(15, result.getDayOfMonth());
        assertEquals(17, result.getHour());
        assertEquals(0, result.getMinute());
    }

    @Test
    void parseDateTimePrecision_dateOnly_fillsEndOfDay() {
        LocalDateTime result = CommonDateParser.parseDateTimePrecision("2024-01-15");
        assertNotNull(result);
        assertEquals(2024, result.getYear());
        assertEquals(1, result.getMonthValue());
        assertEquals(15, result.getDayOfMonth());
        assertEquals(23, result.getHour());
        assertEquals(59, result.getMinute());
        assertEquals(59, result.getSecond());
    }

    @Test
    void parseDateTimePrecision_invalidFormat_returnsNull() {
        assertNull(CommonDateParser.parseDateTimePrecision("invalid-datetime"));
    }

    @Test
    void parseDateTimePrecisionOrThrow_valid_returnsDateTime() {
        LocalDateTime result = CommonDateParser.parseDateTimePrecisionOrThrow("2024-01-15 17:00", "测试日期时间");
        assertNotNull(result);
        assertEquals(2024, result.getYear());
    }

    @Test
    void parseDateTimePrecisionOrThrow_invalid_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> CommonDateParser.parseDateTimePrecisionOrThrow("invalid", "测试日期时间"));
    }

    @Test
    void parseMonthPrecision_null_returnsNull() {
        assertNull(CommonDateParser.parseMonthPrecision(null));
    }

    @Test
    void parseMonthPrecision_blank_returnsNull() {
        assertNull(CommonDateParser.parseMonthPrecision("   "));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2024-01", "2024/01", "2024.01", "2024年1月", "2024年01月", "1/2024"})
    void parseMonthPrecision_variousMonthFormats_returnsFirstDayOfMonth(String input) {
        LocalDate result = CommonDateParser.parseMonthPrecision(input);
        assertNotNull(result);
        assertEquals(2024, result.getYear());
        assertEquals(1, result.getMonthValue());
        assertEquals(1, result.getDayOfMonth());
    }

    @Test
    void parseMonthPrecision_fullDate_truncatesToMonth() {
        LocalDate result = CommonDateParser.parseMonthPrecision("2024-01-15");
        assertNotNull(result);
        assertEquals(2024, result.getYear());
        assertEquals(1, result.getMonthValue());
        assertEquals(1, result.getDayOfMonth());
    }

    @Test
    void parseMonthPrecision_invalidFormat_returnsNull() {
        assertNull(CommonDateParser.parseMonthPrecision("invalid-month"));
    }

    @Test
    void parseMonthPrecisionOrThrow_valid_returnsDate() {
        LocalDate result = CommonDateParser.parseMonthPrecisionOrThrow("2024-01", "测试月份");
        assertNotNull(result);
        assertEquals(2024, result.getYear());
    }

    @Test
    void parseMonthPrecisionOrThrow_invalid_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> CommonDateParser.parseMonthPrecisionOrThrow("invalid", "测试月份"));
    }

    // ===== 边界用例：闰年 =====

    @Test
    void parseDayPrecision_leapYearFeb29_returnsDate() {
        LocalDate result = CommonDateParser.parseDayPrecision("2024-02-29");
        assertNotNull(result);
        assertEquals(2, result.getMonthValue());
        assertEquals(29, result.getDayOfMonth());
    }

    @Test
    void parseDayPrecision_nonLeapYearFeb29_smartModeCorrectsTo28th() {
        // JDK DateTimeFormatter 默认 SMART 模式会将 2023-02-29 自动纠正为 2023-02-28
        // 这是 JDK 标准行为，不是 bug
        LocalDate result = CommonDateParser.parseDayPrecision("2023-02-29");
        assertNotNull(result);
        assertEquals(2, result.getMonthValue());
        assertEquals(28, result.getDayOfMonth());
    }

    // ===== 边界用例：非法日期 =====

    @Test
    void parseDayPrecision_monthOverflow_returnsNull() {
        assertNull(CommonDateParser.parseDayPrecision("2024-13-01"));
    }

    @Test
    void parseDayPrecision_monthZero_returnsNull() {
        assertNull(CommonDateParser.parseDayPrecision("2024-00-15"));
    }

    @Test
    void parseDayPrecision_dayOverflow_returnsNull() {
        assertNull(CommonDateParser.parseDayPrecision("2024-01-32"));
    }

    // ===== 边界用例：格式歧义 =====

    @Test
    void parseDayPrecision_slashAmbiguous_prefersDMYFormat() {
        // 01/02/2024 — SLASH_DMY (d/M/yyyy) 排在 US_DATE (MM/dd/yyyy) 之前
        // 因此解析为 1日2月，而非 2日1月
        // 注意：这意味着 US_DATE (MM/dd/yyyy) 实际被 SLASH_DMY 遮蔽，永远不会被触发
        LocalDate result = CommonDateParser.parseDayPrecision("01/02/2024");
        assertNotNull(result);
        assertEquals(2, result.getMonthValue());
        assertEquals(1, result.getDayOfMonth());
    }

    // ===== 边界用例：超长输入 =====

    @Test
    void parseDayPrecision_extraText_returnsNull() {
        assertNull(CommonDateParser.parseDayPrecision("2024-01-15-ExtraText"));
    }

    // ===== parseAdaptive：自动识别日精度/月精度 =====

    @Test
    void parseAdaptive_null_returnsNull() {
        assertNull(CommonDateParser.parseAdaptive(null));
    }

    @Test
    void parseAdaptive_blank_returnsNull() {
        assertNull(CommonDateParser.parseAdaptive("   "));
    }

    @Test
    void parseAdaptive_dayPrecision_returnsDayDate() {
        LocalDate result = CommonDateParser.parseAdaptive("2024-01-15");
        assertNotNull(result);
        assertEquals(15, result.getDayOfMonth());
    }

    @Test
    void parseAdaptive_monthPrecision_returnsFirstDayOfMonth() {
        LocalDate result = CommonDateParser.parseAdaptive("2024-01");
        assertNotNull(result);
        assertEquals(1, result.getDayOfMonth());
        assertEquals(1, result.getMonthValue());
    }

    @Test
    void parseAdaptive_slashMonth_returnsFirstDayOfMonth() {
        // 之前 PersonnelExcelImporter 的 length()==7 && charAt(4)=='-' 检测会漏掉此格式
        LocalDate result = CommonDateParser.parseAdaptive("2024/01");
        assertNotNull(result);
        assertEquals(1, result.getDayOfMonth());
    }

    @Test
    void parseAdaptive_chineseMonth_returnsFirstDayOfMonth() {
        // 之前 PersonnelExcelImporter 的检测会漏掉此格式（长度 != 7）
        LocalDate result = CommonDateParser.parseAdaptive("2024年1月");
        assertNotNull(result);
        assertEquals(1, result.getDayOfMonth());
    }

    @Test
    void parseAdaptive_invalid_returnsNull() {
        assertNull(CommonDateParser.parseAdaptive("invalid-date"));
    }

    @Test
    void parseAdaptiveOrThrow_valid_returnsDate() {
        LocalDate result = CommonDateParser.parseAdaptiveOrThrow("2024-01-15", "测试");
        assertNotNull(result);
    }

    @Test
    void parseAdaptiveOrThrow_invalid_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> CommonDateParser.parseAdaptiveOrThrow("invalid", "测试"));
    }

    // ===== Excel 日期序列号支持 =====
    // 场景：用户从其他 Excel 复制日期粘贴到非日期格式单元格，值变成纯数字序列号

    @Test
    void parseDayPrecision_excelSerial_45306_returns2024_01_15() {
        // 45306 = 2024-01-15 in Excel date serial (1900 windowing)
        LocalDate result = CommonDateParser.parseDayPrecision("45306");
        assertNotNull(result);
        assertEquals(LocalDate.of(2024, 1, 15), result);
    }

    @Test
    void parseDayPrecision_excelSerial_1_returns1900_01_01() {
        // 序列号 1 = 1900-01-01（Excel 1900 windowing 起点）
        LocalDate result = CommonDateParser.parseDayPrecision("1");
        assertNotNull(result);
        assertEquals(LocalDate.of(1900, 1, 1), result);
    }

    @Test
    void parseDayPrecision_excelSerial_59_returns1900_02_28() {
        // 序列号 59 = 1900-02-28（1900-02-29 bug 之前一天）
        LocalDate result = CommonDateParser.parseDayPrecision("59");
        assertNotNull(result);
        assertEquals(LocalDate.of(1900, 2, 28), result);
    }

    @Test
    void parseDayPrecision_excelSerial_60_handles1900LeapYearBug() {
        // 序列号 60 = Excel 错误的 1900-02-29（实际不存在）
        // 转换时序列号 >= 60 减 1 天，所以序列号 60 → 1900-02-28
        LocalDate result = CommonDateParser.parseDayPrecision("60");
        assertNotNull(result);
        assertEquals(LocalDate.of(1900, 2, 28), result);
    }

    @Test
    void parseDayPrecision_excelSerial_61_returns1900_03_01() {
        // 序列号 61 = 1900-03-01（1900-02-29 bug 之后一天）
        LocalDate result = CommonDateParser.parseDayPrecision("61");
        assertNotNull(result);
        assertEquals(LocalDate.of(1900, 3, 1), result);
    }

    @Test
    void parseDayPrecision_excelSerial_zero_returnsNull() {
        // 序列号 0 超出范围（下限 1）
        assertNull(CommonDateParser.parseDayPrecision("0"));
    }

    @Test
    void parseDayPrecision_excelSerial_negative_returnsNull() {
        // 负数超出范围
        assertNull(CommonDateParser.parseDayPrecision("-1"));
    }

    @Test
    void parseDayPrecision_excelSerial_tooLarge_returnsNull() {
        // 序列号 60001 超出上限 60000（约 2064 年）
        assertNull(CommonDateParser.parseDayPrecision("60001"));
    }

    @Test
    void parseDayPrecision_excelSerial_decimal_returnsDate() {
        // 浮点序列号（带小数部分表示时间，但日精度解析只取日期部分）
        LocalDate result = CommonDateParser.parseDayPrecision("45306.5");
        assertNotNull(result);
        assertEquals(LocalDate.of(2024, 1, 15), result);
    }

    @Test
    void parseAdaptive_excelSerial_returnsDate() {
        // parseAdaptive 也应支持 Excel 日期序列号（人员导入场景）
        LocalDate result = CommonDateParser.parseAdaptive("45306");
        assertNotNull(result);
        assertEquals(LocalDate.of(2024, 1, 15), result);
    }
}