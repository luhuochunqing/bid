// Input: Raw string values from LLM output (budget strings, date strings)
// Output: Parsed Java types – Optional<BigDecimal>, Optional<LocalDate>, Optional<LocalDateTime>
//         Empty Optional signals parse failure (malformed input, null, "约"/"预计" keywords)
// Pos: biddraftagent/infrastructure/openai
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.biddraftagent.infrastructure.openai;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class TenderFieldParser {

    private static final BigDecimal WAN_UNIT = new BigDecimal("10000");
    private static final Pattern BUDGET_NUMBER =
            Pattern.compile("^(\\d+(?:\\.\\d{1,2})?)(?:\\s*(万元|万|元))?$");
    private static final Pattern DATE_VALUE =
            Pattern.compile("^(\\d{4})-(\\d{2})-(\\d{2})$");
    private static final Pattern DATE_TIME_VALUE =
            Pattern.compile("^(\\d{4})-(\\d{2})-(\\d{2})T(\\d{2}):(\\d{2}):(\\d{2})$");

    private TenderFieldParser() {
    }

    static Optional<BigDecimal> parseBudget(String value) {
        String normalized = normalizeNumber(value);
        if (normalized == null) return Optional.empty();
        Matcher matcher = BUDGET_NUMBER.matcher(normalized);
        if (!matcher.matches()) return Optional.empty();
        BigDecimal amount = new BigDecimal(matcher.group(1));
        String unit = matcher.group(2);
        return Optional.of(unit != null && unit.contains("万") ? amount.multiply(WAN_UNIT) : amount);
    }

    static Optional<LocalDate> parsePublishDate(String value) {
        Matcher matcher = DATE_VALUE.matcher(trimToEmpty(value));
        if (!matcher.matches()) return Optional.empty();
        return dateFromParts(matcher.group(1), matcher.group(2), matcher.group(3));
    }

    static Optional<LocalDateTime> parseDeadline(String value) {
        String trimmed = trimToEmpty(value);
        Matcher matcher = DATE_TIME_VALUE.matcher(trimmed);
        if (matcher.matches()) {
            return dateFromParts(matcher.group(1), matcher.group(2), matcher.group(3))
                    .map(d -> d.atTime(
                            twoDigitInt(matcher.group(4)), twoDigitInt(matcher.group(5)), twoDigitInt(matcher.group(6))));
        }
        return parsePublishDate(trimmed)
                .map(d -> d.atTime(23, 59, 59));
    }

    private static Optional<LocalDate> dateFromParts(String yearText, String monthText, String dayText) {
        try {
            return Optional.of(LocalDate.of(
                    Integer.parseInt(yearText),
                    Integer.parseInt(monthText),
                    Integer.parseInt(dayText)
            ));
        } catch (NumberFormatException | java.time.DateTimeException e) {
            return Optional.empty();
        }
    }

    private static int twoDigitInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String normalizeNumber(String value) {
        String trimmed = trimToEmpty(value);
        if (trimmed.isBlank() || trimmed.contains("约") || trimmed.contains("预计")) return null;
        return trimmed.replace(",", "").replace("人民币", "").trim();
    }

    static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
