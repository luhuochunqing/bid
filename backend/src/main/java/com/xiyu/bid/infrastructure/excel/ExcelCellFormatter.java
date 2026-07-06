package com.xiyu.bid.infrastructure.excel;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FormulaEvaluator;

public final class ExcelCellFormatter {

    private ExcelCellFormatter() {
    }

    public static String formatCell(Cell cell, DataFormatter fmt) {
        if (cell == null) {
            return "";
        }
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue().toLocalDate().toString();
        }
        return fmt.formatCellValue(cell).trim();
    }

    public static String formatCell(Cell cell, DataFormatter fmt, FormulaEvaluator evaluator) {
        if (cell == null) {
            return "";
        }
        if (cell.getCellType() == CellType.FORMULA) {
            if (evaluator != null) {
                return fmt.formatCellValue(cell, evaluator).trim();
            }
            return fmt.formatCellValue(cell).trim();
        }
        return formatCell(cell, fmt);
    }
}
