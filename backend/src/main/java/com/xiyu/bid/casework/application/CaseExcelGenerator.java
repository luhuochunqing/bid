package com.xiyu.bid.casework.application;

import com.xiyu.bid.casework.domain.model.CaseExportRecord;
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
public final class CaseExcelGenerator {

    private static final int MAX_COLUMN_WIDTH = 8000;

    private static final int MIN_COLUMN_WIDTH = 2000;

    private static final int MAX_EXPORT_RECORDS = 10000;

    public record ExportResult(byte[] data, int recordCount) {}

    public ExportResult generate(final List<CaseExportRecord> records) throws IOException {
        List<CaseExportRecord> limitedRecords = records;
        if (records != null && records.size() > MAX_EXPORT_RECORDS) {
            limitedRecords = records.subList(0, MAX_EXPORT_RECORDS);
        }
        if (limitedRecords == null) {
            limitedRecords = List.of();
        }

        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("案例库台账");
            CellStyle headerStyle = createHeaderStyle(workbook);

            Row headerRow = sheet.createRow(0);
            String[] headers = CaseExportRecord.HEADERS;
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowNum = 1;
            for (CaseExportRecord record : limitedRecords) {
                Row row = sheet.createRow(rowNum++);
                String[] values = record.toRowValues();
                for (int i = 0; i < values.length; i++) {
                    String val = values[i];
                    row.createCell(i).setCellValue(val != null ? val : "");
                }
            }

            autoSizeColumns(sheet, headers.length);
            workbook.write(out);
            return new ExportResult(out.toByteArray(), limitedRecords.size());
        }
    }

    private CellStyle createHeaderStyle(final Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private void autoSizeColumns(final Sheet sheet, final int columnCount) {
        for (int i = 0; i < columnCount; i++) {
            sheet.autoSizeColumn(i);
            int width = sheet.getColumnWidth(i);
            if (width < MIN_COLUMN_WIDTH) {
                sheet.setColumnWidth(i, MIN_COLUMN_WIDTH);
            } else if (width > MAX_COLUMN_WIDTH) {
                sheet.setColumnWidth(i, MAX_COLUMN_WIDTH);
            }
        }
    }
}
