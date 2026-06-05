// Input: Apache POI Cell
// Output: String / LocalDateTime
// Pos: service/Excel cell reader

package com.xiyu.bid.tender.service;

import org.springframework.stereotype.Component;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Reads typed values from Apache POI cells for tender import.
 */
@Component
public final class TenderExcelCellReader {

    /** Date-time patterns tried in order. */
    static final List<DateTimeFormatter> DATETIME_PATTERNS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")
    );

    /** Date-only patterns tried in order. */
    static final List<DateTimeFormatter> DATE_PATTERNS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd")
    );

    /** Default hour for date-only values. */
    private static final int HOUR_END = 23;
    /** Default minute for date-only values. */
    private static final int MINUTE_END = 59;

    /** Threshold for representing a double as long. */
    private static final double LONG_MAX_EXACT = 1e15;

    /**
     * Reads a cell as a trimmed string, or null if blank.
     *
     * @param cell the POI cell to read
     * @return trimmed string value or null
     */
    public String readString(final Cell cell) {
        if (cell == null) {
            return null;
        }
        final String raw = switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> formatNumeric(cell);
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> safeFormulaText(cell);
            default -> null;
        };
        if (raw == null) {
            return null;
        }
        final String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * Reads a cell as date-time, trying multiple patterns.
     *
     * @param cell the POI cell to read
     * @param label field label for error messages
     * @return parsed LocalDateTime or null
     */
    public LocalDateTime readDateTime(final Cell cell, final String label) {
        if (cell == null) {
            return null;
        }
        if (cell.getCellType() == CellType.NUMERIC
                && DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue();
        }
        final String text = readString(cell);
        if (text == null) {
            return null;
        }
        for (final DateTimeFormatter fmt : DATETIME_PATTERNS) {
            try {
                return LocalDateTime.parse(text, fmt);
            } catch (DateTimeParseException ignored) {
                /* try next pattern */
            }
        }
        for (final DateTimeFormatter fmt : DATE_PATTERNS) {
            try {
                return LocalDate.parse(text, fmt)
                        .atTime(HOUR_END, MINUTE_END);
            } catch (DateTimeParseException ignored) {
                /* try next pattern */
            }
        }
        throw new IllegalArgumentException(
                label + " format error: " + text);
    }

    private String formatNumeric(final Cell cell) {
        if (DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue().toString();
        }
        final double d = cell.getNumericCellValue();
        if (d == Math.floor(d) && !Double.isInfinite(d)
                && Math.abs(d) < LONG_MAX_EXACT) {
            return String.valueOf((long) d);
        }
        return BigDecimal.valueOf(d)
                .stripTrailingZeros().toPlainString();
    }

    private String safeFormulaText(final Cell cell) {
        try {
            return cell.getStringCellValue();
        } catch (IllegalStateException ex) {
            return BigDecimal.valueOf(cell.getNumericCellValue())
                    .stripTrailingZeros().toPlainString();
        }
    }
}
