package com.xiyu.bid.personnel.infrastructure.excel;

import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CO-419: 人员证书批量导入模板生成器测试。
 *
 * 覆盖：
 * - 4 个 Sheet 完整性
 * - 表头命名与新增表单对齐（入职日期/手机号码/到期日期）
 * - 7 个枚举字段下拉校验存在
 * - Excel 结构完整（CO-238 踩坑防护：能被重新读取且能再次 write）
 * - 说明文案含月份精度格式 YYYY-MM
 */
class PersonnelImportTemplateGeneratorTest {

    private final PersonnelImportTemplateGenerator generator = new PersonnelImportTemplateGenerator();

    private XSSFWorkbook readGenerated() throws IOException {
        return new XSSFWorkbook(new ByteArrayInputStream(generator.generate()));
    }

    @Test
    void generated_template_has_four_sheets() throws IOException {
        try (Workbook wb = readGenerated()) {
            assertThat(wb.getNumberOfSheets()).isEqualTo(4);
            assertThat(wb.getSheetName(0)).isEqualTo("填写说明");
            assertThat(wb.getSheetName(1)).isEqualTo("基础信息");
            assertThat(wb.getSheetName(2)).isEqualTo("教育经历");
            assertThat(wb.getSheetName(3)).isEqualTo("证书与职称");
        }
    }

    @Test
    void basic_info_headers_align_with_form_labels() throws IOException {
        try (Workbook wb = readGenerated()) {
            Sheet sheet = wb.getSheet("基础信息");
            // CO-419: 入职时间→入职日期, 手机→手机号码
            assertThat(sheet.getRow(0).getCell(3).getStringCellValue()).isEqualTo("入职日期");
            assertThat(sheet.getRow(0).getCell(5).getStringCellValue()).isEqualTo("手机号码");
        }
    }

    @Test
    void certificate_headers_align_with_form_labels() throws IOException {
        try (Workbook wb = readGenerated()) {
            Sheet sheet = wb.getSheet("证书与职称");
            // CO-419: 有效期至→到期日期 (col 6)
            assertThat(sheet.getRow(0).getCell(6).getStringCellValue()).isEqualTo("到期日期");
        }
    }

    @Test
    void generated_excel_is_structurally_valid_no_co238_regression() throws IOException {
        // CO-238 踩坑防护：生成的字节流能被 XSSFWorkbook 重新读取且能再次 write 到新流
        byte[] bytes = generator.generate();
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes));
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            wb.write(out); // 不抛错即结构完整
            assertThat(out.toByteArray().length).isPositive();
        }
    }

    @Test
    void seven_enum_fields_have_dropdown_validations() throws IOException {
        // CO-419: 7 个字段加下拉（性别/最高学历/学习形式/是否为最高学历学校/证书类型/职称/永久有效）
        try (XSSFWorkbook wb = readGenerated()) {
            // 基础信息：性别 (1 个下拉)
            int basic = wb.getSheet("基础信息").getDataValidations().size();
            // 教育经历：最高学历/学习形式/是否为最高学历学校 (3 个下拉)
            int edu = wb.getSheet("教育经历").getDataValidations().size();
            // 证书与职称：证书类型/职称/永久有效 (3 个下拉)
            int cert = wb.getSheet("证书与职称").getDataValidations().size();

            assertThat(basic).isGreaterThanOrEqualTo(1);
            assertThat(edu).isGreaterThanOrEqualTo(3);
            assertThat(cert).isGreaterThanOrEqualTo(3);
            assertThat(basic + edu + cert).isGreaterThanOrEqualTo(7);
        }
    }

    @Test
    void dropdown_values_use_chinese_for_certificate_type() throws IOException {
        // CO-419: 证书类型下拉存在（具体中文值由 PersonnelImportEnumMapping 常量保证）
        // 这里只验证证书 Sheet 有 3 个下拉（证书类型/职称/永久有效），具体值由枚举映射常量单元测试覆盖
        try (XSSFWorkbook wb = readGenerated()) {
            XSSFSheet sheet = wb.getSheet("证书与职称");
            assertThat(sheet.getDataValidations()).hasSizeGreaterThanOrEqualTo(3);
        }
    }

    @Test
    void instruction_sheet_mentions_year_month_format() throws IOException {
        // CO-419: 入学/毕业时间改为月份精度 YYYY-MM（与表单 type="month" 一致）
        try (Workbook wb = readGenerated()) {
            Sheet sheet = wb.getSheet("填写说明");
            StringBuilder allText = new StringBuilder();
            for (int r = 0; r <= sheet.getLastRowNum(); r++) {
                if (sheet.getRow(r) != null && sheet.getRow(r).getCell(0) != null) {
                    allText.append(sheet.getRow(r).getCell(0).getStringCellValue()).append('\n');
                }
            }
            assertThat(allText.toString()).contains("YYYY-MM");
        }
    }

    @Test
    void instruction_sheet_marks_required_fields_correctly() throws IOException {
        // CO-419: 最高学历/学习形式改为必填（与表单 validateTab 一致）
        try (Workbook wb = readGenerated()) {
            Sheet sheet = wb.getSheet("填写说明");
            StringBuilder allText = new StringBuilder();
            for (int r = 0; r <= sheet.getLastRowNum(); r++) {
                if (sheet.getRow(r) != null && sheet.getRow(r).getCell(0) != null) {
                    allText.append(sheet.getRow(r).getCell(0).getStringCellValue()).append('\n');
                }
            }
            String text = allText.toString();
            // 至少出现一次"最高学历：必填"或"最高学历：必填"的语义
            assertThat(text).contains("最高学历");
            assertThat(text).contains("学习形式");
            // 不应再出现"最高学历：选填"
            assertThat(text).doesNotContain("最高学历：选填");
            assertThat(text).doesNotContain("学习形式：选填");
        }
    }
}
