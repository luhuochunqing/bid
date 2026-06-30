package com.xiyu.bid.common.util;

import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class ExcelAutoSizeHelperTest {

    @Test
    void autoSizeColumns_normalCase_setsWidthWithinBounds() throws IOException {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet();
            sheet.createRow(0).createCell(0).setCellValue("测试内容测试内容");
            sheet.createRow(1).createCell(0).setCellValue("短");

            ExcelAutoSizeHelper.autoSizeColumns(sheet, 1);

            assertThat(sheet.getColumnWidth(0)).isBetween(2000, 8000);

            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                wb.write(out);
                assertThat(out.size()).isGreaterThan(0);
            }
        }
    }

    @Test
    void autoSizeColumns_withCustomMinMax_respectsBounds() throws IOException {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet();
            sheet.createRow(0).createCell(0).setCellValue("a");

            ExcelAutoSizeHelper.autoSizeColumns(sheet, 1, 3000, 5000);

            assertThat(sheet.getColumnWidth(0)).isGreaterThanOrEqualTo(3000);
        }
    }

    @Test
    void autoSizeColumns_zeroColumns_doesNothing() throws IOException {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet();
            assertThatCode(() -> ExcelAutoSizeHelper.autoSizeColumns(sheet, 0))
                    .doesNotThrowAnyException();
        }
    }
}
