package com.xiyu.bid.warehouse.domain;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;

/**
 * 纯核心：仓库导入日期解析器 — 支持多种常见日期格式兜底解析。
 * 不含 I/O、不含副作用、不修改入参。
 */
public final class WarehouseDateParser {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter LENIENT_ISO = DateTimeFormatter.ofPattern("yyyy-M-d");
    private static final DateTimeFormatter SLASH = DateTimeFormatter.ofPattern("yyyy/M/d");
    private static final DateTimeFormatter DOT = DateTimeFormatter.ofPattern("yyyy.M.d");
    private static final DateTimeFormatter CHINESE = DateTimeFormatter.ofPattern("yyyy年M月d日");
    private static final DateTimeFormatter DASH_DMY = DateTimeFormatter.ofPattern("d-M-yyyy");
    private static final DateTimeFormatter SLASH_DMY = DateTimeFormatter.ofPattern("d/M/yyyy");

    private WarehouseDateParser() {
    }

    /**
     * 尝试以 ISO 优先、常见格式兜底的顺序解析日期字符串。
     *
     * @param text 待解析文本，允许前后空白
     * @return 解析成功返回 {@link LocalDate}；空值、空白或无法识别返回 null
     */
    public static LocalDate parse(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String trimmed = text.trim();
        return tryParse(trimmed, ISO)
                .or(() -> tryParse(trimmed, LENIENT_ISO))
                .or(() -> tryParse(trimmed, SLASH))
                .or(() -> tryParse(trimmed, DOT))
                .or(() -> tryParse(trimmed, CHINESE))
                .or(() -> tryParse(trimmed, DASH_DMY))
                .or(() -> tryParse(trimmed, SLASH_DMY))
                .orElse(null);
    }

    private static Optional<LocalDate> tryParse(String text, DateTimeFormatter formatter) {
        try {
            return Optional.of(LocalDate.parse(text, formatter));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }
}
