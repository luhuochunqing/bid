package com.xiyu.bid.common.util;

import org.apache.poi.ss.usermodel.Sheet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 统一 Excel 列宽自动调整入口，防御服务器字体缺失导致的 "Fontconfig head is null" 异常。
 *
 * <p>策略：
 * <ol>
 *   <li>尝试 autoSizeColumn，成功则限制宽度在 [minWidth, maxWidth] 范围内</li>
 *   <li>首列失败 → 记录警告，剩余列直接跳过 autoSize，全部使用 fallback 固定宽度</li>
 * </ol>
 *
 * <p>CO-438: 之前每列独立 try-catch，字体系统损坏时每列都触发一次失败的字体初始化，
 * 不仅浪费性能，还可能导致 Sentry 重复上报。改为首列失败后整批降级。
 */
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
        boolean fontAvailable = true;
        for (int i = 0; i < columnCount; i++) {
            if (!fontAvailable) {
                sheet.setColumnWidth(i, DEFAULT_FALLBACK_WIDTH);
                continue;
            }
            try {
                sheet.autoSizeColumn(i);
                int width = sheet.getColumnWidth(i);
                if (width < minWidth) sheet.setColumnWidth(i, minWidth);
                if (width > maxWidth) sheet.setColumnWidth(i, maxWidth);
            } catch (RuntimeException e) {
                fontAvailable = false;
                log.warn("autoSizeColumn 首列失败（列索引={}），字体系统可能不可用，剩余列将使用 fallback 列宽 {}：{}",
                        i, DEFAULT_FALLBACK_WIDTH, e.getMessage());
                sheet.setColumnWidth(i, DEFAULT_FALLBACK_WIDTH);
            }
        }
    }
}
