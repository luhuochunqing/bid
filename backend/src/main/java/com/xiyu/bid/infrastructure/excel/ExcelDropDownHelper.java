package com.xiyu.bid.infrastructure.excel;

import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationHelper;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddressList;

/**
 * Excel 下拉选择辅助工具 — 统一封装 POI DataValidation API，
 * 供各导入模板生成器复用，避免重复实现。
 */
public final class ExcelDropDownHelper {

    private static final int DEFAULT_FIRST_DATA_ROW = 1;
    private static final int DEFAULT_LAST_DATA_ROW = 999;

    private ExcelDropDownHelper() {}

    /**
     * 为指定列添加下拉选择（默认范围：第2行到第1000行）。
     *
     * @param sheet       目标 Sheet
     * @param columnIndex 列索引（从0开始）
     * @param options     下拉选项数组
     */
    public static void addDropdown(Sheet sheet, int columnIndex, String[] options) {
        addDropdown(sheet, columnIndex, options, DEFAULT_FIRST_DATA_ROW, DEFAULT_LAST_DATA_ROW);
    }

    /**
     * 为指定列添加下拉选择（自定义行范围）。
     *
     * @param sheet       目标 Sheet
     * @param columnIndex 列索引（从0开始）
     * @param options     下拉选项数组
     * @param firstRow    起始行（从0开始，0=第一行）
     * @param lastRow     结束行（从0开始）
     */
    public static void addDropdown(Sheet sheet, int columnIndex, String[] options,
                                    int firstRow, int lastRow) {
        DataValidationHelper helper = sheet.getDataValidationHelper();
        CellRangeAddressList rangeList = new CellRangeAddressList(firstRow, lastRow, columnIndex, columnIndex);
        DataValidation validation = helper.createValidation(
                helper.createFormulaListConstraint(createInlineFormula(options)),
                rangeList);
        validation.setShowErrorBox(true);
        validation.setSuppressDropDownArrow(true);
        sheet.addValidationData(validation);
    }

    /**
     * POI 5.x DataValidationHelper 要求 formula 以引号包裹的逗号分隔列表形式提供。
     * 例：'"男,女"'
     */
    private static String createInlineFormula(String[] options) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < options.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(options[i]);
        }
        sb.append('"');
        return sb.toString();
    }
}
