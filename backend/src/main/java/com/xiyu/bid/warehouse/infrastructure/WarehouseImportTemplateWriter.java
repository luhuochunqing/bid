package com.xiyu.bid.warehouse.infrastructure;

import java.util.List;
import com.xiyu.bid.warehouse.domain.WarehouseImportPolicy;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationConstraint;
import org.apache.poi.ss.usermodel.DataValidationHelper;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddressList;
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
            hints[WarehouseImportPolicy.COL_START_DATE] = "支持 YYYY-MM-DD、YYYY/M/D、YYYY.MM.DD、YYYY年M月D日";
            hints[WarehouseImportPolicy.COL_END_DATE] = "支持 YYYY-MM-DD、YYYY/M/D、YYYY.MM.DD、YYYY年M月D日";
            hints[WarehouseImportPolicy.COL_LESSOR] = "出租方/服务方";
            hints[WarehouseImportPolicy.COL_LESSEE] = "承租方";
            hints[WarehouseImportPolicy.COL_INVOICE_START] = "支持 YYYY-MM-DD、YYYY/M/D、YYYY.MM.DD、YYYY年M月D日";
            hints[WarehouseImportPolicy.COL_INVOICE_END] = "支持 YYYY-MM-DD、YYYY/M/D、YYYY.MM.DD、YYYY年M月D日";
            hints[WarehouseImportPolicy.COL_CLOSE_PLAN] = "关仓计划说明";
            hints[WarehouseImportPolicy.COL_HAS_PROPERTY_CERT] = "是 / 否";
            hints[WarehouseImportPolicy.COL_PROPERTY_CERT_FILE] = "产权证=是时必填，请输入文件名";
            hints[WarehouseImportPolicy.COL_HAS_INVOICE] = "是 / 否";
            hints[WarehouseImportPolicy.COL_INVOICE_FILE] = "发票=是时必填，请输入文件名";
            hints[WarehouseImportPolicy.COL_HAS_PHOTOS] = "是 / 否";
            hints[WarehouseImportPolicy.COL_PHOTOS_FILE] = "照片=是时必填，请输入文件名";
            hints[WarehouseImportPolicy.COL_LEASE_CONTRACT_FILE_NAME] = "有租赁合同时必填，请输入文件名";
            hints[WarehouseImportPolicy.COL_CERT_REMARKS] = "选填";
            for (int i = 0; i < hints.length; i++) {
                Cell c = hint.createCell(i);
                c.setCellValue(hints[i] != null ? hints[i] : "");
            }

            // 给枚举列加下拉框，避免用户自由填写导致导入失败
            applyDropDownValidations(sheet);

            wb.write(out);
            return out.toByteArray();
        }
    }

    /**
     * 给模板中的枚举列加 Excel 数据有效性下拉框：
     * - 仓库类型（COL_TYPE）：自营 / 云仓
     * - 所在省份（COL_PROVINCE）：34 个省级行政区
     * - 所属区域（COL_REGION）：华北 / 东北 / 华东 / 华中 / 华南 / 西北 / 西南
     * - 是否有产权证 / 是否有发票 / 是否有仓库照片：是 / 否
     *
     * 所有选项统一取自 WarehouseImportPolicy，避免 domain 与 infrastructure 重复定义。
     * 数据行范围从第 2 行（hint 行之后）到 65535 行，覆盖用户实际可填的所有行。
     */
    private void applyDropDownValidations(Sheet sheet) {
        DataValidationHelper helper = sheet.getDataValidationHelper();
        addListValidation(sheet, helper, WarehouseImportPolicy.TYPE_OPTIONS, WarehouseImportPolicy.COL_TYPE);
        addListValidation(sheet, helper, WarehouseImportPolicy.PROVINCE_OPTIONS, WarehouseImportPolicy.COL_PROVINCE);
        addListValidation(sheet, helper, WarehouseImportPolicy.REGION_OPTIONS, WarehouseImportPolicy.COL_REGION);
        addListValidation(sheet, helper, WarehouseImportPolicy.YES_NO_OPTIONS, WarehouseImportPolicy.COL_HAS_PROPERTY_CERT);
        addListValidation(sheet, helper, WarehouseImportPolicy.YES_NO_OPTIONS, WarehouseImportPolicy.COL_HAS_INVOICE);
        addListValidation(sheet, helper, WarehouseImportPolicy.YES_NO_OPTIONS, WarehouseImportPolicy.COL_HAS_PHOTOS);
    }

    private void addListValidation(Sheet sheet, DataValidationHelper helper, String[] options, int col) {
        DataValidationConstraint constraint = helper.createExplicitListConstraint(options);
        // 从第 2 行（hint 行 = 第 1 行，用户数据从第 2 行开始）到 65535 行
        CellRangeAddressList range = new CellRangeAddressList(2, 65535, col, col);
        DataValidation validation = helper.createValidation(constraint, range);
        validation.setSuppressDropDownArrow(true);
        validation.setShowErrorBox(true);
        sheet.addValidationData(validation);
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
