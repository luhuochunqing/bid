package com.xiyu.bid.resources.domain;

import com.xiyu.bid.infrastructure.excel.SingleSheetExcelReader;
import com.xiyu.bid.infrastructure.excel.SingleSheetExcelReader.WorkbookData;
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

/**
 * 复现 CA 证书导入 yyyy/M/d 格式日期解析失败的端到端测试。
 * 通过 SingleSheetExcelReader 读取 Excel → CaCertificateImportPolicy.parseRow() 解析日期。
 */
class CaCertificateImportDateE2ETest {

    @Test
    @DisplayName("yyyy/M/d 日期格式单元格 → SingleSheetExcelReader → CaCertificateImportPolicy 全链路")
    void slashDateFormat_endToEnd() throws IOException {
        String[] formats = {
            "yyyy/M/d",
            "yyyy/m/d",
            "yyyy/MM/dd",
            "yyyy-mm-dd",
        };
        for (String fmt : formats) {
            byte[] excel = buildCaCertificateExcel(LocalDate.of(2024, 1, 15), fmt);
            SingleSheetExcelReader reader = new SingleSheetExcelReader();
            WorkbookData data = reader.read(excel);

            String[] row = data.data().get(0);
            String expiryDateStr = row[CaCertificateImportPolicy.COL_EXPIRY_DATE];

            CaCertificateImportPolicy.ParsedCaRow parsed = CaCertificateImportPolicy.parseRow(2, row);

            assertThat(parsed.expiryDate())
                .as("Format '%s' → cellValue='%s' should parse successfully", fmt, expiryDateStr)
                .isEqualTo(LocalDate.of(2024, 1, 15));
        }
    }

    @Test
    @DisplayName("文本单元格 yyyy/M/d → 全链路解析")
    void textCellSlashDateFormat_endToEnd() throws IOException {
        byte[] excel = buildCaCertificateExcelWithTextDate("2024/1/15");
        SingleSheetExcelReader reader = new SingleSheetExcelReader();
        WorkbookData data = reader.read(excel);

        String[] row = data.data().get(0);
        String expiryDateStr = row[CaCertificateImportPolicy.COL_EXPIRY_DATE];

        CaCertificateImportPolicy.ParsedCaRow parsed = CaCertificateImportPolicy.parseRow(2, row);

        assertThat(parsed.expiryDate())
            .as("Text cell '2024/1/15' → cellValue='%s' should parse", expiryDateStr)
            .isEqualTo(LocalDate.of(2024, 1, 15));
    }

    @Test
    @DisplayName("文本单元格 yyyy/M/d 带前导零 → 全链路解析")
    void textCellSlashDateWithLeadingZeros_endToEnd() throws IOException {
        byte[] excel = buildCaCertificateExcelWithTextDate("2024/01/15");
        SingleSheetExcelReader reader = new SingleSheetExcelReader();
        WorkbookData data = reader.read(excel);

        String[] row = data.data().get(0);
        CaCertificateImportPolicy.ParsedCaRow parsed = CaCertificateImportPolicy.parseRow(2, row);

        assertThat(parsed.expiryDate())
            .isEqualTo(LocalDate.of(2024, 1, 15));
    }

    private byte[] buildCaCertificateExcel(LocalDate date, String formatStr) throws IOException {
        try (Workbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("CA证书导入模板");

            // Header
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < CaCertificateImportPolicy.HEADERS.length; i++) {
                headerRow.createCell(i).setCellValue(CaCertificateImportPolicy.HEADERS[i]);
            }

            // Data row
            Row dataRow = sheet.createRow(1);
            dataRow.createCell(0).setCellValue("实体CA");
            dataRow.createCell(1).setCellValue("公章");
            dataRow.createCell(2).setCellValue("张三");
            dataRow.createCell(3).setCellValue("李四");

            Cell dateCell = dataRow.createCell(4);
            dateCell.setCellValue(date);
            DataFormat df = wb.createDataFormat();
            CellStyle style = wb.createCellStyle();
            style.setDataFormat(df.getFormat(formatStr));
            dateCell.setCellStyle(style);

            dataRow.createCell(5).setCellValue("颁发机构");

            wb.write(out);
            return out.toByteArray();
        }
    }

    private byte[] buildCaCertificateExcelWithTextDate(String dateText) throws IOException {
        try (Workbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("CA证书导入模板");

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < CaCertificateImportPolicy.HEADERS.length; i++) {
                headerRow.createCell(i).setCellValue(CaCertificateImportPolicy.HEADERS[i]);
            }

            Row dataRow = sheet.createRow(1);
            dataRow.createCell(0).setCellValue("实体CA");
            dataRow.createCell(1).setCellValue("公章");
            dataRow.createCell(2).setCellValue("张三");
            dataRow.createCell(3).setCellValue("李四");
            dataRow.createCell(4).setCellValue(dateText);
            dataRow.createCell(5).setCellValue("颁发机构");

            wb.write(out);
            return out.toByteArray();
        }
    }
}
