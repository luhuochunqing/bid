package com.xiyu.bid.common.util;

import org.apache.poi.ss.usermodel.Sheet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ExcelAutoSizeHelper {

    private static final Logger log = LoggerFactory.getLogger(ExcelAutoSizeHelper.class);

    private static final int DEFAULT_MIN_WIDTH = 2000;
    private static final int DEFAULT_MAX_WIDTH = 8000;
    private static final int DEFAULT_FALLBACK_WIDTH = 4000;

    private ExcelAutoSizeHelper() {}

    public static void autoSizeColumns(Sheet sheet, int columnCount) {
        autoSizeColumns(sheet, columnCount, DEFAULT_MIN_WIDTH, DEFAULT_MAX_WIDTH);
    }

    public static void autoSizeColumns(Sheet sheet, int columnCount, int minWidth, int maxWidth) {
        for (int i = 0; i < columnCount; i++) {
            try {
                sheet.autoSizeColumn(i);
                int width = sheet.getColumnWidth(i);
                if (width < minWidth) sheet.setColumnWidth(i, minWidth);
                if (width > maxWidth) sheet.setColumnWidth(i, maxWidth);
            } catch (RuntimeException e) {
                log.warn("autoSizeColumn 失败（列索引={}），使用 fallback 列宽 {}：{}",
                        i, DEFAULT_FALLBACK_WIDTH, e.getMessage());
                sheet.setColumnWidth(i, DEFAULT_FALLBACK_WIDTH);
            }
        }
    }
}
