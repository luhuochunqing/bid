package com.xiyu.bid.infrastructure.excel;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class ExcelCellFormatterTest {

    private static final DataFormatter FMT = new DataFormatter();

    @Nested
    @DisplayName("日期单元格")
    class DateCells {

        @Test
        @DisplayName("多种 Excel 日期格式统一输出 yyyy-MM-dd")
        void variousDateFormats_outputIsoFormat() throws IOException {
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
                try (Workbook wb = createWorkbook()) {
                    Cell cell = createDateCell(wb, expected, fmt);
                    String result = ExcelCellFormatter.formatCell(cell, FMT);
                    assertThat(result)
                        .as("Format '%s' should output ISO date", fmt)
                        .isEqualTo("2024-01-15");
                }
            }
        }

        @Test
        @DisplayName("日期单元格 null 安全")
        void nullCell_returnsEmptyString() {
            assertThat(ExcelCellFormatter.formatCell(null, FMT)).isEmpty();
        }
    }

    @Nested
    @DisplayName("文本单元格")
    class TextCells {

        @Test
        @DisplayName("文本单元格保持原样并 trim")
        void textCell_preservedAndTrimmed() throws IOException {
            try (Workbook wb = createWorkbook()) {
                Cell cell = createTextCell(wb, "  hello  ");
                assertThat(ExcelCellFormatter.formatCell(cell, FMT)).isEqualTo("hello");
            }
        }

        @Test
        @DisplayName("日期格式的文本字符串不被特殊处理")
        void dateLikeText_preservedAsText() throws IOException {
            try (Workbook wb = createWorkbook()) {
                Cell cell = createTextCell(wb, "2024/01/15");
                assertThat(ExcelCellFormatter.formatCell(cell, FMT)).isEqualTo("2024/01/15");
            }
        }
    }

    @Nested
    @DisplayName("数字单元格")
    class NumericCells {

        @Test
        @DisplayName("非日期数字单元格走 DataFormatter")
        void numericCell_dataFormatterOutput() throws IOException {
            try (Workbook wb = createWorkbook()) {
                Cell cell = createNumericCell(wb, 1234.56, "0.00");
                assertThat(ExcelCellFormatter.formatCell(cell, FMT)).isEqualTo("1234.56");
            }
        }

        @Test
        @DisplayName("整数数字保持格式")
        void integerNumericCell_formatted() throws IOException {
            try (Workbook wb = createWorkbook()) {
                Cell cell = createNumericCell(wb, 42, "0");
                assertThat(ExcelCellFormatter.formatCell(cell, FMT)).isEqualTo("42");
            }
        }
    }

    @Nested
    @DisplayName("公式单元格")
    class FormulaCells {

        @Test
        @DisplayName("带 evaluator 的公式单元格走 DataFormatter + evaluator")
        void formulaCell_withEvaluator() throws IOException {
            try (Workbook wb = createWorkbook()) {
                Sheet sheet = wb.getSheetAt(0);
                Row row = sheet.createRow(0);
                row.createCell(0).setCellValue(10);
                row.createCell(1).setCellValue(20);
                Cell formulaCell = row.createCell(2);
                formulaCell.setCellFormula("A1+B1");
                var evaluator = wb.getCreationHelper().createFormulaEvaluator();
                String result = ExcelCellFormatter.formatCell(formulaCell, FMT, evaluator);
                assertThat(result).isEqualTo("30");
            }
        }
    }

    private Workbook createWorkbook() {
        XSSFWorkbook wb = new XSSFWorkbook();
        wb.createSheet("Test");
        return wb;
    }

    private Cell createDateCell(Workbook wb, LocalDate date, String formatStr) {
        Sheet sheet = wb.getSheetAt(0);
        Row row = sheet.createRow(0);
        Cell cell = row.createCell(0);
        cell.setCellValue(date);
        DataFormat df = wb.createDataFormat();
        CellStyle style = wb.createCellStyle();
        style.setDataFormat(df.getFormat(formatStr));
        cell.setCellStyle(style);
        return cell;
    }

    private Cell createTextCell(Workbook wb, String text) {
        Sheet sheet = wb.getSheetAt(0);
        Row row = sheet.createRow(0);
        Cell cell = row.createCell(0);
        cell.setCellValue(text);
        return cell;
    }

    private Cell createNumericCell(Workbook wb, double value, String formatStr) {
        Sheet sheet = wb.getSheetAt(0);
        Row row = sheet.createRow(0);
        Cell cell = row.createCell(0);
        cell.setCellValue(value);
        DataFormat df = wb.createDataFormat();
        CellStyle style = wb.createCellStyle();
        style.setDataFormat(df.getFormat(formatStr));
        cell.setCellStyle(style);
        return cell;
    }
}
