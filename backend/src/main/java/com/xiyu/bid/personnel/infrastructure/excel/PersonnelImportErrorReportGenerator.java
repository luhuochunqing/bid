package com.xiyu.bid.personnel.infrastructure.excel;

import com.xiyu.bid.personnel.domain.importvalidation.ImportValidationError;
import com.xiyu.bid.personnel.domain.importvalidation.ImportValidationWarning;
import com.xiyu.bid.personnel.domain.importvalidation.ValidationResult;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

@Component
public class PersonnelImportErrorReportGenerator {

    private static final String[] ERROR_HEADERS = {
            "Sheet名称", "Excel行号", "工号", "姓名", "出错字段", "错误描述"
    };

    private static final String[] WARNING_HEADERS = {
            "Sheet名称", "Excel行号", "工号", "姓名", "警告描述"
    };

    public byte[] generateErrorReport(ValidationResult validationResult) throws IOException {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            CellStyle headerStyle = createHeaderStyle(workbook);

            Sheet errorSheet = workbook.createSheet("错误明细");
            createErrorHeader(errorSheet, headerStyle);
            fillErrorRows(errorSheet, validationResult.errors());

            Sheet warningSheet = workbook.createSheet("警告明细");
            createWarningHeader(warningSheet, headerStyle);
            fillWarningRows(warningSheet, validationResult.warnings());

            workbook.write(out);
            return out.toByteArray();
        }
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private void createErrorHeader(Sheet sheet, CellStyle headerStyle) {
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < ERROR_HEADERS.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(ERROR_HEADERS[i]);
            cell.setCellStyle(headerStyle);
            sheet.setColumnWidth(i, 20 * 256);
        }
    }

    private void createWarningHeader(Sheet sheet, CellStyle headerStyle) {
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < WARNING_HEADERS.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(WARNING_HEADERS[i]);
            cell.setCellStyle(headerStyle);
            sheet.setColumnWidth(i, 25 * 256);
        }
    }

    private void fillErrorRows(Sheet sheet, List<ImportValidationError> errors) {
        if (errors == null || errors.isEmpty()) {
            Row emptyRow = sheet.createRow(1);
            Cell cell = emptyRow.createCell(0);
            cell.setCellValue("无错误");
            return;
        }

        int rowNum = 1;
        for (ImportValidationError error : errors) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(nvl(error.sheet()));
            row.createCell(1).setCellValue(error.rowNumber() != null ? error.rowNumber() : 0);
            row.createCell(2).setCellValue(nvl(error.employeeNumber()));
            row.createCell(3).setCellValue("");
            row.createCell(4).setCellValue(nvl(error.field()));
            row.createCell(5).setCellValue(nvl(error.message()));
        }
    }

    private void fillWarningRows(Sheet sheet, List<ImportValidationWarning> warnings) {
        if (warnings == null || warnings.isEmpty()) {
            Row emptyRow = sheet.createRow(1);
            Cell cell = emptyRow.createCell(0);
            cell.setCellValue("无警告");
            return;
        }

        int rowNum = 1;
        for (ImportValidationWarning warning : warnings) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(nvl(warning.sheet()));
            row.createCell(1).setCellValue(warning.rowNumber() != null ? warning.rowNumber() : 0);
            row.createCell(2).setCellValue(nvl(warning.employeeNumber()));
            row.createCell(3).setCellValue("");
            row.createCell(4).setCellValue(nvl(warning.message()));
        }
    }

    private String nvl(String s) {
        return s != null ? s : "";
    }
}
