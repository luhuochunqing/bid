package com.xiyu.bid.brandauth.manufacturer.application.service;

import com.xiyu.bid.brandauth.manufacturer.domain.valueobject.ProductLine;
import com.xiyu.bid.exception.BusinessException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Brand auth Excel 导入解析工具（纯静态工具类，不依赖 Spring）。
 *
 * <p>从 BrandAuthImportService 拆出，保持主类职责清晰 + 控制行数。</p>
 */
final class BrandAuthImportParser {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private BrandAuthImportParser() {}

    static String getCellString(final Row row, final int col) {
        Cell cell = row.getCell(col);
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                double val = cell.getNumericCellValue();
                yield val == Math.floor(val) && !Double.isInfinite(val)
                        ? String.valueOf((long) val) : String.valueOf(val);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                try { yield String.valueOf((int) cell.getNumericCellValue()); }
                catch (RuntimeException e) {
                    try { yield cell.getStringCellValue().trim(); }
                    catch (RuntimeException e2) { yield ""; }
                }
            }
            default -> "";
        };
    }

    static LocalDate parseDate(final String str) {
        if (str == null || str.isBlank()) return null;
        try {
            return LocalDate.parse(str.trim(), DATE_FMT);
        } catch (DateTimeParseException e) {
            throw new BusinessException("日期格式错误 (" + str + ")，应为 yyyy-MM-dd");
        }
    }

    static ProductLine parseProductLine(final String str) {
        if (str == null || str.isBlank()) return null;
        return ProductLine.fromStringOptional(str)
                .orElseThrow(() -> new BusinessException("无效的一级产线: " + str));
    }

    static void validateRequired(final String value, final String fieldName) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(fieldName + "不能为空");
        }
    }
}
