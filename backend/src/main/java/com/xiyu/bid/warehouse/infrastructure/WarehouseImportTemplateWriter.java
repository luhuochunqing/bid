package com.xiyu.bid.warehouse.infrastructure;

import java.util.List;
import com.xiyu.bid.warehouse.domain.WarehouseImportPolicy;
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

/**
 * 基础设施：仓库批量导入模板生成器。
 */
@Component
public class WarehouseImportTemplateWriter {

    private static final String SHEET_NAME = "仓库导入模板";

    public byte[] write() throws IOException {
        try (Workbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle headerStyle = createHeaderStyle(wb);
            Sheet sheet = wb.createSheet(SHEET_NAME);

            Row header = sheet.createRow(0);
            String[] headers = WarehouseImportPolicy.TEMPLATE_HEADERS;
            for (int i = 0; i < headers.length; i++) {
                Cell c = header.createCell(i);
                c.setCellValue(headers[i]);
                c.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, 22 * 256);
            }

            Row hint = sheet.createRow(1);
            String[] hints = new String[headers.length];
            hints[WarehouseImportPolicy.COL_NAME] = "示例：北京朝阳仓（系统会替换特殊字符为 _）";
            hints[WarehouseImportPolicy.COL_TYPE] = "自营 或 云仓";
            hints[WarehouseImportPolicy.COL_PROVINCE] = "如：北京市";
            hints[WarehouseImportPolicy.COL_ADDRESS] = "街道地址";
            hints[WarehouseImportPolicy.COL_AREA] = "数字，单位 ㎡";
            hints[WarehouseImportPolicy.COL_REGION] = "如：华北/华东";
            hints[WarehouseImportPolicy.COL_CONTACT] = "联系人姓名";
            hints[WarehouseImportPolicy.COL_REMARKS] = "选填";
            hints[WarehouseImportPolicy.COL_START_DATE] = "YYYY-MM-DD";
            hints[WarehouseImportPolicy.COL_END_DATE] = "YYYY-MM-DD";
            hints[WarehouseImportPolicy.COL_LESSOR] = "出租方/服务方";
            hints[WarehouseImportPolicy.COL_LESSEE] = "承租方";
            hints[WarehouseImportPolicy.COL_INVOICE_START] = "YYYY-MM-DD";
            hints[WarehouseImportPolicy.COL_INVOICE_END] = "YYYY-MM-DD";
            hints[WarehouseImportPolicy.COL_CLOSE_PLAN] = "关仓计划说明";
            hints[WarehouseImportPolicy.COL_HAS_PROPERTY_CERT] = "是 / 否";
            hints[WarehouseImportPolicy.COL_PROPERTY_CERT_FILE] = "产权证=是时必填，对应文件名";
            hints[WarehouseImportPolicy.COL_HAS_INVOICE] = "是 / 否";
            hints[WarehouseImportPolicy.COL_INVOICE_FILE] = "发票=是时必填，对应文件名";
            hints[WarehouseImportPolicy.COL_HAS_PHOTOS] = "是 / 否";
            hints[WarehouseImportPolicy.COL_PHOTOS_FILE] = "照片=是时必填，对应文件名";
            hints[WarehouseImportPolicy.COL_CERT_REMARKS] = "选填";
            for (int i = 0; i < hints.length; i++) {
                Cell c = hint.createCell(i);
                c.setCellValue(hints[i] != null ? hints[i] : "");
            }

            wb.write(out);
            return out.toByteArray();
        }
    }

    private CellStyle createHeaderStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_50_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    /**
     * 将任意表头 + 数据行写入 Excel，用于生成修正文件等场景。
     */
    public byte[] writeWithExtraColumns(String[] headers, List<String[]> rows) throws IOException {
        try (Workbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle headerStyle = createHeaderStyle(wb);
            Sheet sheet = wb.createSheet(SHEET_NAME);
            Row hr = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell c = hr.createCell(i);
                c.setCellValue(headers[i]);
                c.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, 18 * 256);
            }
            int rowNum = 1;
            for (String[] row : rows) {
                Row r = sheet.createRow(rowNum++);
                for (int i = 0; i < headers.length; i++) {
                    String val = (row != null && i < row.length && row[i] != null) ? row[i] : "";
                    r.createCell(i).setCellValue(val);
                }
            }
            wb.write(out);
            return out.toByteArray();
        }
    }
}
