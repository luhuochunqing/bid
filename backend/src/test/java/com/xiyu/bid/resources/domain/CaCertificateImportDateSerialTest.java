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
 * 复现 CA 证书导入中 Excel 日期序列号解析失败的场景。
 *
 * 场景：用户从其他 Excel 复制日期值，粘贴到模板中。
 * 如果目标单元格格式不是日期格式（如 General 或文本），
 * 粘贴的值是 Excel 日期序列号（如 45306），而不是日期字符串。
 * CommonDateParser 无法解析纯数字，导致"有效期至格式错误"。
 */
class CaCertificateImportDateSerialTest {

    @Test
    @DisplayName("Excel 日期序列号（NUMERIC + General 格式）→ 应解析为日期")
    void excelDateSerial_generalFormat_shouldParseAsDate() throws IOException {
        // 45306 = 2024-01-15 in Excel date serial
        double excelSerial = 45306.0;

        byte[] excel = buildCaCertificateExcelWithNumericDate(excelSerial, "General");
        SingleSheetExcelReader reader = new SingleSheetExcelReader();
        WorkbookData data = reader.read(excel);

        String[] row = data.data().get(0);
        String expiryDateStr = row[CaCertificateImportPolicy.COL_EXPIRY_DATE];

        // 当前行为：DataFormatter 输出 "45306"，parseDayPrecision 无法解析
        CaCertificateImportPolicy.ParsedCaRow parsed = CaCertificateImportPolicy.parseRow(2, row);

        // 期望：应该解析为 2024-01-15
        assertThat(parsed.expiryDate())
            .as("Excel serial %s (cellValue='%s') should parse as date", excelSerial, expiryDateStr)
            .isEqualTo(LocalDate.of(2024, 1, 15));
    }

    @Test
    @DisplayName("Excel 日期序列号（NUMERIC + 文本格式）→ 应解析为日期")
    void excelDateSerial_textFormat_shouldParseAsDate() throws IOException {
        double excelSerial = 45306.0;

        byte[] excel = buildCaCertificateExcelWithNumericDate(excelSerial, "@");
        SingleSheetExcelReader reader = new SingleSheetExcelReader();
        WorkbookData data = reader.read(excel);

        String[] row = data.data().get(0);
        String expiryDateStr = row[CaCertificateImportPolicy.COL_EXPIRY_DATE];

        CaCertificateImportPolicy.ParsedCaRow parsed = CaCertificateImportPolicy.parseRow(2, row);

        assertThat(parsed.expiryDate())
            .as("Excel serial %s with text format (cellValue='%s') should parse as date", excelSerial, expiryDateStr)
            .isEqualTo(LocalDate.of(2024, 1, 15));
    }

    private byte[] buildCaCertificateExcelWithNumericDate(double serial, String formatStr) throws IOException {
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

            Cell dateCell = dataRow.createCell(4);
            dateCell.setCellValue(serial);
            DataFormat df = wb.createDataFormat();
            CellStyle style = wb.createCellStyle();
            style.setDataFormat(df.getFormat(formatStr));
            dateCell.setCellStyle(style);

            dataRow.createCell(5).setCellValue("颁发机构");

            wb.write(out);
            return out.toByteArray();
        }
    }
}
