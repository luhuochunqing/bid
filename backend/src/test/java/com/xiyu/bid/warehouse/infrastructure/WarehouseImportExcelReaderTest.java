package com.xiyu.bid.warehouse.infrastructure;

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

class WarehouseImportExcelReaderTest {

    @Test
    @DisplayName("read 将 Excel 日期格式单元格转换为 ISO 日期字符串")
    void readsDateFormattedCellAsIsoString() throws IOException {
        byte[] bytes = createWorkbookWithDateCell(LocalDate.of(2026, 7, 3));
        WarehouseImportExcelReader.SheetData sheetData = new WarehouseImportExcelReader().read(bytes);
        assertThat(sheetData.rows).hasSize(1);
        assertThat(sheetData.rows.get(0)).containsExactly("2026-07-03");
    }

    @Test
    @DisplayName("read 对普通文本单元格仍使用 DataFormatter 输出")
    void readsPlainTextCellAsString() throws IOException {
        byte[] bytes = createWorkbookWithTextCell("自营");
        WarehouseImportExcelReader.SheetData sheetData = new WarehouseImportExcelReader().read(bytes);
        assertThat(sheetData.rows.get(0)).containsExactly("自营");
    }

    private byte[] createWorkbookWithDateCell(LocalDate date) throws IOException {
        try (Workbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet();
            Row row = sheet.createRow(0);
            Cell cell = row.createCell(0);
            cell.setCellValue(date);

            DataFormat df = wb.createDataFormat();
            CellStyle style = wb.createCellStyle();
            style.setDataFormat(df.getFormat("yyyy-MM-dd"));
            cell.setCellStyle(style);

            wb.write(out);
            return out.toByteArray();
        }
    }

    private byte[] createWorkbookWithTextCell(String text) throws IOException {
        try (Workbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet();
            Row row = sheet.createRow(0);
            Cell cell = row.createCell(0);
            cell.setCellValue(text);
            wb.write(out);
            return out.toByteArray();
        }
    }
}
