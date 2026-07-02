package com.xiyu.bid.performance.application.service;

import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationConstraint;
import org.apache.poi.ss.usermodel.DataValidationHelper;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * 业绩导入模板生成服务
 */
@Service
public class PerformanceExcelTemplateGenerator {

    private static final String[] TEMPLATE_HEADERS = {
            "合同名称", "签约单位", "集团公司名称", "客户类型", "所属行业",
            "项目类型", "对接方式", "客户级别", "合同是否含西域",
            "签约日期", "截止日期", "总截止日期", "到期天数", "到期提醒",
            "客户联系人", "客户联系方式", "属地", "客户地址", "合同中西域项目负责人",
            "合同协议附件文件名", "客户商城网站网址", "商城对接截图附件文件名",
            "国资委央企名录截图附件文件名", "品类页附件文件名",
            "签约抬头与央企集团关系证明附件文件名",
            "是否有中标通知书", "中标通知书附件文件名", "备注"
    };

    private static final String[] CUSTOMER_TYPE_OPTIONS = {
            "政府机关/事业单位", "央企", "地方国企", "民企", "港澳台及外企"
    };

    private static final String[] PROJECT_TYPE_OPTIONS = {
            "办公", "综合", "集采", "工业品", "其他"
    };

    private static final String[] DOCKING_METHOD_OPTIONS = {
            "Emall", "Punch-out", "API"
    };

    private static final String[] CUSTOMER_LEVEL_OPTIONS = {
            "集团", "二级单位"
    };

    private static final String[] YES_NO_OPTIONS = {"是", "否"};

    private static final int MAX_ROWS = 1000;

    public byte[] generate() throws IOException {
        try (var wb = new XSSFWorkbook()) {
            var sheet = wb.createSheet("业绩导入模板");
            var headerRow = sheet.createRow(0);
            var headerStyle = wb.createCellStyle();
            var font = wb.createFont();
            font.setBold(true);
            headerStyle.setFont(font);
            headerStyle.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            for (int i = 0; i < TEMPLATE_HEADERS.length; i++) {
                var cell = headerRow.createCell(i);
                cell.setCellValue(TEMPLATE_HEADERS[i]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, 20 * 256);
            }

            addDropdownValidation(sheet, 3, CUSTOMER_TYPE_OPTIONS);
            addDropdownValidation(sheet, 5, PROJECT_TYPE_OPTIONS);
            addDropdownValidation(sheet, 6, DOCKING_METHOD_OPTIONS);
            addDropdownValidation(sheet, 7, CUSTOMER_LEVEL_OPTIONS);
            addDropdownValidation(sheet, 8, YES_NO_OPTIONS);
            addDropdownValidation(sheet, 25, YES_NO_OPTIONS);

            var out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        }
    }

    private void addDropdownValidation(org.apache.poi.xssf.usermodel.XSSFSheet sheet, int colIndex, String[] options) {
        DataValidationHelper helper = sheet.getDataValidationHelper();
        DataValidationConstraint constraint = helper.createExplicitListConstraint(options);
        CellRangeAddressList range = new CellRangeAddressList(1, MAX_ROWS, colIndex, colIndex);
        DataValidation validation = helper.createValidation(constraint, range);
        validation.setEmptyCellAllowed(true);
        validation.setShowErrorBox(true);
        validation.setShowPromptBox(true);
        validation.createPromptBox("输入提示", "请从下拉列表中选择");
        validation.createErrorBox("输入错误", "请从下拉列表中选择有效值");
        sheet.addValidationData(validation);
    }
}
