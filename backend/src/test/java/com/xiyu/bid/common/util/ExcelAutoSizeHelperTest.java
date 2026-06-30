package com.xiyu.bid.common.util;

import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.*;

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

    /**
     * CO-438: autoSizeColumn 抛异常时（模拟字体系统不可用），
     * 首列失败后所有列应降级为 fallback 固定宽度，不抛异常。
     */
    @Test
    void autoSizeColumns_fontUnavailable_fallsBackToFixedWidth() {
        Sheet sheet = mock(Sheet.class);
        // 模拟字体系统不可用：autoSizeColumn 抛 RuntimeException
        doThrow(new RuntimeException("Fontconfig head is null"))
                .when(sheet).autoSizeColumn(anyInt());

        ExcelAutoSizeHelper.autoSizeColumns(sheet, 3);

        // 所有列都应设置为 fallback 宽度
        verify(sheet).setColumnWidth(0, 4000);
        verify(sheet).setColumnWidth(1, 4000);
        verify(sheet).setColumnWidth(2, 4000);
        // autoSizeColumn 只应被调用一次（首列失败后跳过剩余）
        verify(sheet, times(1)).autoSizeColumn(anyInt());
    }

    /**
     * CO-438: 首列失败后，剩余列不应再尝试 autoSizeColumn，
     * 避免字体系统损坏时重复触发失败的字体初始化。
     */
    @Test
    void autoSizeColumns_firstColumnFails_skipsAutoSizeForRemaining() {
        Sheet sheet = mock(Sheet.class);
        doThrow(new RuntimeException("Fontconfig head is null"))
                .when(sheet).autoSizeColumn(0);

        ExcelAutoSizeHelper.autoSizeColumns(sheet, 5);

        // autoSizeColumn 只被调用 1 次（首列），剩余 4 列直接跳过
        verify(sheet, times(1)).autoSizeColumn(anyInt());
        // 所有 5 列都设置为 fallback 宽度
        for (int i = 0; i < 5; i++) {
            verify(sheet).setColumnWidth(i, 4000);
        }
    }
}
