package com.xiyu.bid.common.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;

public final class CommonDateParser {

    private CommonDateParser() {
    }

    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter LENIENT_ISO = DateTimeFormatter.ofPattern("yyyy-M-d");
    private static final DateTimeFormatter SLASH_DATE = DateTimeFormatter.ofPattern("yyyy/M/d");
    private static final DateTimeFormatter DOT_DATE = DateTimeFormatter.ofPattern("yyyy.M.d");
    private static final DateTimeFormatter CHINESE_DATE_SHORT = DateTimeFormatter.ofPattern("yyyy年M月d日");
    private static final DateTimeFormatter CHINESE_DATE_FULL = DateTimeFormatter.ofPattern("yyyy年MM月dd日");
    private static final DateTimeFormatter DASH_DMY = DateTimeFormatter.ofPattern("d-M-yyyy");
    private static final DateTimeFormatter SLASH_DMY = DateTimeFormatter.ofPattern("d/M/yyyy");
    private static final DateTimeFormatter US_DATE = DateTimeFormatter.ofPattern("MM/dd/yyyy");
    private static final DateTimeFormatter GERMAN_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private static final DateTimeFormatter ISO_DATE_TIME_SECONDS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter ISO_DATE_TIME_MINUTES = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter SLASH_DATE_TIME_SECONDS = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
    private static final DateTimeFormatter SLASH_DATE_TIME_MINUTES = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm");
    private static final DateTimeFormatter CHINESE_DATE_TIME_MINUTES = DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm");
    private static final DateTimeFormatter CHINESE_DATE_TIME_SECONDS = DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm:ss");

    private static final DateTimeFormatter ISO_MONTH = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter SLASH_MONTH = DateTimeFormatter.ofPattern("yyyy/MM");
    private static final DateTimeFormatter DOT_MONTH = DateTimeFormatter.ofPattern("yyyy.MM");
    private static final DateTimeFormatter CHINESE_MONTH_SHORT = DateTimeFormatter.ofPattern("yyyy年M月");
    private static final DateTimeFormatter CHINESE_MONTH_FULL = DateTimeFormatter.ofPattern("yyyy年MM月");
    private static final DateTimeFormatter US_MONTH = DateTimeFormatter.ofPattern("M/yyyy");

    private static final List<DateTimeFormatter> DAY_PATTERNS = List.of(
            ISO_DATE,
            LENIENT_ISO,
            SLASH_DATE,
            DOT_DATE,
            CHINESE_DATE_SHORT,
            CHINESE_DATE_FULL,
            DASH_DMY,
            SLASH_DMY,
            US_DATE,
            GERMAN_DATE
    );

    private static final List<DateTimeFormatter> DATETIME_PATTERNS = List.of(
            ISO_DATE_TIME_SECONDS,
            ISO_DATE_TIME_MINUTES,
            SLASH_DATE_TIME_SECONDS,
            SLASH_DATE_TIME_MINUTES,
            CHINESE_DATE_TIME_MINUTES,
            CHINESE_DATE_TIME_SECONDS
    );

    public static LocalDate parseDayPrecision(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String trimmed = text.trim();
        LocalDate day = tryParseDay(trimmed, DAY_PATTERNS).orElse(null);
        if (day != null) {
            return day;
        }
        return tryParseExcelSerial(trimmed).orElse(null);
    }

    public static LocalDate parseDayPrecisionOrThrow(String text, String fieldName) {
        LocalDate result = parseDayPrecision(text);
        if (result == null) {
            throw new IllegalArgumentException(fieldName + "日期格式错误: \"" + text + "\"，支持格式: yyyy-MM-dd / yyyy/M/d / yyyy.M.d / yyyy年M月d日 / d-M-yyyy / d/M/yyyy / MM/dd/yyyy / dd.MM.yyyy");
        }
        return result;
    }

    public static LocalDateTime parseDateTimePrecision(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String trimmed = text.trim();
        for (DateTimeFormatter fmt : DATETIME_PATTERNS) {
            try {
                return LocalDateTime.parse(trimmed, fmt);
            } catch (DateTimeParseException ignored) {
            }
        }
        LocalDate date = tryParseDay(trimmed, DAY_PATTERNS).orElse(null);
        if (date != null) {
            return date.atTime(23, 59, 59);
        }
        return null;
    }

    public static LocalDateTime parseDateTimePrecisionOrThrow(String text, String fieldName) {
        LocalDateTime result = parseDateTimePrecision(text);
        if (result == null) {
            throw new IllegalArgumentException(fieldName + "日期时间格式错误: \"" + text + "\"，支持格式: yyyy-MM-dd HH:mm:ss / yyyy-MM-dd HH:mm / yyyy/MM/dd HH:mm:ss / yyyy/MM/dd HH:mm / yyyy年M月d日 HH:mm / yyyy-MM-dd(补23:59:59)");
        }
        return result;
    }

    public static LocalDate parseMonthPrecision(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String trimmed = text.trim();
        if (trimmed.length() == 7 && trimmed.charAt(4) == '-') {
            try {
                return YearMonth.parse(trimmed).atDay(1);
            } catch (DateTimeParseException ignored) {
            }
        }
        for (DateTimeFormatter fmt : List.of(SLASH_MONTH, DOT_MONTH, CHINESE_MONTH_SHORT, CHINESE_MONTH_FULL, US_MONTH)) {
            try {
                return YearMonth.parse(trimmed, fmt).atDay(1);
            } catch (DateTimeParseException ignored) {
            }
        }
        LocalDate fullDate = tryParseDay(trimmed, DAY_PATTERNS).orElse(null);
        if (fullDate != null) {
            return LocalDate.of(fullDate.getYear(), fullDate.getMonth(), 1);
        }
        return null;
    }

    public static LocalDate parseMonthPrecisionOrThrow(String text, String fieldName) {
        LocalDate result = parseMonthPrecision(text);
        if (result == null) {
            throw new IllegalArgumentException(fieldName + "月份格式错误: \"" + text + "\"，支持格式: yyyy-MM / yyyy/MM / yyyy.MM / yyyy年M月 / M/yyyy / yyyy-MM-dd(自动截取年月)");
        }
        return result;
    }

    /**
     * 自适应解析：先尝试日精度，失败后尝试月精度（补1号）。
     * 用于同一列可能混合日精度和月精度输入的场景（如人员导入的日期字段）。
     *
     * @param text 待解析文本，允许前后空白
     * @return 解析成功返回 {@link LocalDate}；空值、空白或无法识别返回 null
     */
    public static LocalDate parseAdaptive(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String trimmed = text.trim();
        LocalDate day = tryParseDay(trimmed, DAY_PATTERNS).orElse(null);
        if (day != null) {
            return day;
        }
        // 兜底：Excel 日期序列号（用户从其他 Excel 复制粘贴到非日期格式单元格时出现）
        LocalDate serialDate = tryParseExcelSerial(trimmed).orElse(null);
        if (serialDate != null) {
            return serialDate;
        }
        return parseMonthPrecision(trimmed);
    }

    /**
     * 自适应解析，失败时抛出异常。
     *
     * @param text 待解析文本
     * @param fieldName 字段名，用于错误提示
     * @return 解析后的 {@link LocalDate}
     * @throws IllegalArgumentException 如果无法解析
     */
    public static LocalDate parseAdaptiveOrThrow(String text, String fieldName) {
        LocalDate result = parseAdaptive(text);
        if (result == null) {
            throw new IllegalArgumentException(fieldName + "日期格式错误: \"" + text + "\"，支持格式: yyyy-MM-dd / yyyy/M/d / yyyy.M.d / yyyy年M月d日 / d-M-yyyy / d/M/yyyy / MM/dd/yyyy / dd.MM.yyyy / yyyy-MM / yyyy/MM");
        }
        return result;
    }

    private static Optional<LocalDate> tryParseDay(String text, List<DateTimeFormatter> patterns) {
        for (DateTimeFormatter fmt : patterns) {
            try {
                return Optional.of(LocalDate.parse(text, fmt));
            } catch (DateTimeParseException ignored) {
            }
        }
        return Optional.empty();
    }

    /**
     * 尝试将 Excel 日期序列号（如 45306）解析为 {@link LocalDate}。
     *
     * <p>场景：用户从其他 Excel 复制日期值粘贴到模板时，如果目标单元格格式不是日期格式
     * （如 General 或文本），粘贴的值会变成 Excel 日期序列号（纯数字），而非日期字符串。
     * 此方法兜底处理这种情况，避免 "有效期至格式错误" 等误报。</p>
     *
     * <p>Excel 日期序列号（1900 windowing）：
     * <ul>
     *   <li>序列号 1 = 1900-01-01</li>
     *   <li>序列号 60 = 1900-02-29（Excel 1900 闰年 bug，实际不存在）</li>
     *   <li>序列号 45306 = 2024-01-15</li>
     * </ul>
     * 转换公式：序列号 &gt;= 60 时减 1 天跳过 1900-02-29 bug。</p>
     *
     * <p>范围限制：序列号 1-60000（约 1900-01-01 到 2064-05-19），
     * 避免误将普通数字（如价格、ID）解析为日期。</p>
     *
     * @param text 待解析文本，应为纯数字字符串
     * @return 解析成功返回 {@link LocalDate}；非数字或超出范围返回 {@link Optional#empty()}
     */
    private static Optional<LocalDate> tryParseExcelSerial(String text) {
        try {
            double serial = Double.parseDouble(text);
            int days = (int) Math.floor(serial);
            // 范围检查：避免误将普通数字解析为日期
            // 序列号 1 = 1900-01-01，序列号 60000 ≈ 2064-05-19
            if (days < 1 || days > 60000) {
                return Optional.empty();
            }
            // Excel 1900 闰年 bug：序列号 60 对应不存在的 1900-02-29
            // 序列号 >= 60 时减 1 天跳过
            if (days >= 60) {
                days -= 1;
            }
            // 基准：序列号 1 = 1900-01-01 = LocalDate.of(1899, 12, 31).plusDays(1)
            return Optional.of(LocalDate.of(1899, 12, 31).plusDays(days));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}