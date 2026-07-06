package com.xiyu.bid.infrastructure.excel;

import com.xiyu.bid.common.domain.CommonDateParser;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class SingleSheetExcelReaderDateTest {

    @Test
    @DisplayName("日期单元格统一输出 yyyy-MM-dd 格式 — 修复前会按 Excel 格式输出导致解析失败")
    void dateCellOutputsIsoFormat() throws IOException {
        String[] formats = {
            "yyyy-mm-dd",
            "yyyy/m/d",
            "m/d/yyyy",
            "dd/mm/yyyy",
            "yyyy\"年\"m\"月\"d\"日\"",
            "[$-804]yyyy\"年\"m\"月\"d\"日\"",
        };
        LocalDate expected = LocalDate.of(2024, 1, 15);
        for (String fmt : formats) {
            byte[] bytes = createWorkbookWithDateCell(expected, fmt);
            SingleSheetExcelReader reader = new SingleSheetExcelReader();
            SingleSheetExcelReader.WorkbookData data = reader.read(bytes);
            String cellValue = data.data().get(0)[0];
            assertThat(cellValue)
                .as("Format '%s' should output ISO date", fmt)
                .isEqualTo("2024-01-15");
            LocalDate parsed = CommonDateParser.parseDayPrecision(cellValue);
            assertThat(parsed)
                .as("CommonDateParser should parse ISO output from format '%s'", fmt)
                .isEqualTo(expected);
        }
    }

    @Test
    @DisplayName("文本单元格保持原样输出")
    void textCellOutputsAsIs() throws IOException {
        byte[] bytes = createWorkbookWithTextCell("2024/01/15");
        SingleSheetExcelReader reader = new SingleSheetExcelReader();
        SingleSheetExcelReader.WorkbookData data = reader.read(bytes);
        assertThat(data.data().get(0)[0]).isEqualTo("2024/01/15");
    }

    @Test
    @DisplayName("数字单元格保持 DataFormatter 输出")
    void numericCellOutputsFormattedNumber() throws IOException {
        byte[] bytes = createWorkbookWithNumericCell(1234.56, "0.00");
        SingleSheetExcelReader reader = new SingleSheetExcelReader();
        SingleSheetExcelReader.WorkbookData data = reader.read(bytes);
        assertThat(data.data().get(0)[0]).isEqualTo("1234.56");
    }

    private byte[] createWorkbookWithDateCell(LocalDate date, String formatStr) throws IOException {
        try (Workbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet();
            Row headerRow = sheet.createRow(0);
            headerRow.createCell(0).setCellValue("测试列");

            Row row = sheet.createRow(1);
            Cell cell = row.createCell(0);
            cell.setCellValue(date);

            DataFormat df = wb.createDataFormat();
            CellStyle style = wb.createCellStyle();
            style.setDataFormat(df.getFormat(formatStr));
            cell.setCellStyle(style);

            wb.write(out);
            return out.toByteArray();
        }
    }

    private byte[] createWorkbookWithTextCell(String text) throws IOException {
        try (Workbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet();
            Row headerRow = sheet.createRow(0);
            headerRow.createCell(0).setCellValue("测试列");
            Row row = sheet.createRow(1);
            row.createCell(0).setCellValue(text);
            wb.write(out);
            return out.toByteArray();
        }
    }

    private byte[] createWorkbookWithNumericCell(double value, String formatStr) throws IOException {
        try (Workbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet();
            Row headerRow = sheet.createRow(0);
            headerRow.createCell(0).setCellValue("测试列");
            Row row = sheet.createRow(1);
            Cell cell = row.createCell(0);
            cell.setCellValue(value);
            DataFormat df = wb.createDataFormat();
            CellStyle style = wb.createCellStyle();
            style.setDataFormat(df.getFormat(formatStr));
            cell.setCellStyle(style);
            wb.write(out);
            return out.toByteArray();
        }
    }
}
