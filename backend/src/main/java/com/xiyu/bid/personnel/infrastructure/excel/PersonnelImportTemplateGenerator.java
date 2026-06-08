package com.xiyu.bid.personnel.infrastructure.excel;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

@Component
public class PersonnelImportTemplateGenerator {

    private static final String SHEET_BASIC_INFO = "基础信息";
    private static final String SHEET_EDUCATION = "教育经历";
    private static final String SHEET_CERTIFICATES = "证书与职称";

    private static final String[] BASIC_INFO_HEADERS = {
            "工号", "姓名", "性别", "入职时间", "手机", "学历", "技术职称"
    };

    private static final String[] EDUCATION_HEADERS = {
            "工号", "姓名", "学校名称", "入学时间", "毕业时间", "最高学历", "学习形式", "专业"
    };

    private static final String[] CERTIFICATE_HEADERS = {
            "工号", "姓名", "证书名称", "证书编号", "证书类型", "发证日期", "有效期至", "附件文件名"
    };

    private static final String INSTRUCTION_SHEET = "填写说明";

    private static final String[] INSTRUCTION_CONTENT = {
            "【人员证书批量导入模板说明】",
            "",
            "一、文件结构",
            "  本Excel包含4个Sheet，请按各Sheet要求填写数据：",
            "  1. 填写说明：本说明页",
            "  2. 基础信息：必填，每个员工一条记录",
            "  3. 教育经历：选填，可填写多条教育记录",
            "  4. 证书与职称：选填，可填写多条证书记录",
            "",
            "二、基础信息填写规范",
            "  工号：必填，建议使用字母+数字组合，如 EMP001",
            "  姓名：必填",
            "  性别：选填，可填 男/女",
            "  入职时间：格式如 2024-01-15",
            "  手机：选填",
            "  学历：选填，如 博士/硕士/本科/大专/中专等",
            "  技术职称：选填",
            "",
            "三、教育经历填写规范",
            "  工号：必填，需与【基础信息】中的工号一致",
            "  姓名：选填，用于交叉校验",
            "  学校名称：必填",
            "  入学时间/毕业时间：格式如 2024-01-15",
            "  最高学历：选填",
            "  学习形式：选填，如 全日制/非全日制/网络教育等",
            "  专业：选填",
            "",
            "四、证书与职称填写规范",
            "  工号：必填，需与【基础信息】中的工号一致",
            "  姓名：选填，用于交叉校验",
            "  证书名称：必填，如 一级建造师、安全员证等",
            "  证书编号：选填",
            "  证书类型：选填，如 注册类证书/岗位证书/职称证书等",
            "  发证日期：格式如 2024-01-15",
            "  有效期至：格式如 2029-01-15，到期后系统将发送提醒",
            "  附件文件名：请填写已上传附件的文件名，命名规范：",
            "    格式：PER_{姓名}_{工号}_{序号}_{证书名称}.{扩展名}",
            "    示例：PER_张三_EMP001_01_一级建造师.pdf",
            "",
            "五、注意事项",
            "  1. 请勿修改表头行（第一行）",
            "  2. 日期格式请使用 yyyy-MM-dd",
            "  3. 必填字段不可为空",
            "  4. 工号在同一批次中不可重复",
            "  5. 建议单次导入不超过1000条记录"
    };

    public byte[] generate() throws IOException {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle instructionStyle = createInstructionStyle(workbook);

            Sheet instructionSheet = workbook.createSheet(INSTRUCTION_SHEET);
            fillInstructionSheet(instructionSheet, instructionStyle);

            Sheet basicInfoSheet = workbook.createSheet(SHEET_BASIC_INFO);
            createHeaderRow(basicInfoSheet, BASIC_INFO_HEADERS, headerStyle);

            Sheet educationSheet = workbook.createSheet(SHEET_EDUCATION);
            createHeaderRow(educationSheet, EDUCATION_HEADERS, headerStyle);

            Sheet certificatesSheet = workbook.createSheet(SHEET_CERTIFICATES);
            createHeaderRow(certificatesSheet, CERTIFICATE_HEADERS, headerStyle);

            workbook.write(out);
            return out.toByteArray();
        }
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    private CellStyle createInstructionStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setFontHeightInPoints((short) 11);
        style.setFont(font);
        style.setWrapText(true);
        return style;
    }

    private void fillInstructionSheet(Sheet sheet, CellStyle style) {
        for (int i = 0; i < INSTRUCTION_CONTENT.length; i++) {
            Row row = sheet.createRow(i);
            Cell cell = row.createCell(0);
            cell.setCellValue(INSTRUCTION_CONTENT[i]);
            cell.setCellStyle(style);
            sheet.setColumnWidth(0, 80 * 256);
        }
    }

    private void createHeaderRow(Sheet sheet, String[] headers, CellStyle headerStyle) {
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
            sheet.setColumnWidth(i, 18 * 256);
        }

        Row sampleRow = sheet.createRow(1);
        for (int i = 0; i < headers.length; i++) {
            sampleRow.createCell(i).setCellValue("");
        }
    }
}
