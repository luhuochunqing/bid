package com.xiyu.bid.personnel.infrastructure.excel;

import com.xiyu.bid.personnel.infrastructure.excel.PersonnelExcelImporter.ImportResult;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CO-419: 人员证书导入器测试。
 *
 * 覆盖：
 * - 入学/毕业时间支持 YYYY-MM 月份精度解析（与表单 type="month" 一致）
 * - YYYY-MM-DD 完整日期格式保持兼容
 * - 证书类型中文值（建造师等）映射为英文枚举（CONSTRUCTOR 等）存储
 */
class PersonnelExcelImporterTest {

    private final PersonnelExcelImporter importer = new PersonnelExcelImporter();

    private static InputStream buildExcel(BasicInfoRow basic,
                                          EduRow edu,
                                          CertRow cert) throws IOException {
        try (Workbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            // 基础信息 Sheet
            Sheet basicSheet = wb.createSheet("基础信息");
            Row basicHeader = basicSheet.createRow(0);
            basicHeader.createCell(0).setCellValue("工号");
            basicHeader.createCell(1).setCellValue("姓名");
            basicHeader.createCell(2).setCellValue("性别");
            basicHeader.createCell(3).setCellValue("入职日期");
            basicHeader.createCell(4).setCellValue("出生日期");
            basicHeader.createCell(5).setCellValue("手机号码");
            basicHeader.createCell(6).setCellValue("学历");
            basicHeader.createCell(7).setCellValue("技术职称");
            basicHeader.createCell(8).setCellValue("部门");
            basicHeader.createCell(9).setCellValue("备注");
            Row basicRow = basicSheet.createRow(1);
            basicRow.createCell(0).setCellValue(basic.employeeNumber);
            basicRow.createCell(1).setCellValue(basic.name);
            if (basic.gender != null) basicRow.createCell(2).setCellValue(basic.gender);
            if (basic.entryDate != null) basicRow.createCell(3).setCellValue(basic.entryDate);
            if (basic.phone != null) basicRow.createCell(5).setCellValue(basic.phone);

            // 教育经历 Sheet
            Sheet eduSheet = wb.createSheet("教育经历");
            Row eduHeader = eduSheet.createRow(0);
            eduHeader.createCell(0).setCellValue("工号");
            eduHeader.createCell(1).setCellValue("姓名");
            eduHeader.createCell(2).setCellValue("学校名称");
            eduHeader.createCell(3).setCellValue("入学时间");
            eduHeader.createCell(4).setCellValue("毕业时间");
            eduHeader.createCell(5).setCellValue("最高学历");
            eduHeader.createCell(6).setCellValue("学习形式");
            eduHeader.createCell(7).setCellValue("专业");
            eduHeader.createCell(8).setCellValue("是否为最高学历学校");
            Row eduRow = eduSheet.createRow(1);
            eduRow.createCell(0).setCellValue(edu.employeeNumber);
            eduRow.createCell(2).setCellValue(edu.schoolName);
            if (edu.startDate != null) eduRow.createCell(3).setCellValue(edu.startDate);
            if (edu.endDate != null) eduRow.createCell(4).setCellValue(edu.endDate);
            if (edu.highestEducation != null) eduRow.createCell(5).setCellValue(edu.highestEducation);
            if (edu.studyForm != null) eduRow.createCell(6).setCellValue(edu.studyForm);
            if (edu.isHighestSchool != null) eduRow.createCell(8).setCellValue(edu.isHighestSchool);

            // 证书与职称 Sheet
            Sheet certSheet = wb.createSheet("证书与职称");
            Row certHeader = certSheet.createRow(0);
            certHeader.createCell(0).setCellValue("工号");
            certHeader.createCell(1).setCellValue("姓名");
            certHeader.createCell(2).setCellValue("证书名称");
            certHeader.createCell(3).setCellValue("证书编号");
            certHeader.createCell(4).setCellValue("证书类型");
            certHeader.createCell(5).setCellValue("发证日期");
            certHeader.createCell(6).setCellValue("到期日期");
            certHeader.createCell(7).setCellValue("附件文件名");
            certHeader.createCell(8).setCellValue("职称");
            certHeader.createCell(9).setCellValue("永久有效");
            certHeader.createCell(10).setCellValue("备注");
            if (cert != null) {
                Row certRow = certSheet.createRow(1);
                certRow.createCell(0).setCellValue(cert.employeeNumber);
                certRow.createCell(2).setCellValue(cert.certificateName);
                if (cert.type != null) certRow.createCell(4).setCellValue(cert.type);
                if (cert.title != null) certRow.createCell(8).setCellValue(cert.title);
                if (cert.isPermanent != null) certRow.createCell(9).setCellValue(cert.isPermanent);
            }

            wb.write(out);
            return new ByteArrayInputStream(out.toByteArray());
        }
    }

    @Test
    void parse_year_month_date_format_for_education_dates() throws IOException {
        // CO-419: 入学/毕业时间支持 YYYY-MM 月份精度（与表单 type="month" 一致）
        try (InputStream in = buildExcel(
                new BasicInfoRow("EMP001", "张三", "男", "2024-01-15", "13800000000"),
                new EduRow("EMP001", "清华大学", "2020-09", "2024-06", "本科", "全日制", "是"),
                null)) {
            ImportResult result = importer.importFromStream(in);
            assertThat(result.educationRows()).hasSize(1);
            assertThat(result.educationRows().get(0).startDate()).isEqualTo(LocalDate.of(2020, 9, 1));
            assertThat(result.educationRows().get(0).endDate()).isEqualTo(LocalDate.of(2024, 6, 1));
        }
    }

    @Test
    void parse_full_date_format_still_works() throws IOException {
        // 兼容性：YYYY-MM-DD 完整日期格式仍能正常解析
        try (InputStream in = buildExcel(
                new BasicInfoRow("EMP001", "张三", "男", "2024-01-15", "13800000000"),
                new EduRow("EMP001", "清华大学", "2020-09-01", "2024-06-30", "本科", "全日制", "是"),
                null)) {
            ImportResult result = importer.importFromStream(in);
            assertThat(result.educationRows().get(0).startDate()).isEqualTo(LocalDate.of(2020, 9, 1));
            assertThat(result.educationRows().get(0).endDate()).isEqualTo(LocalDate.of(2024, 6, 30));
        }
    }

    @Test
    void map_chinese_certificate_type_to_english_enum() throws IOException {
        // CO-419: 证书类型下拉用中文，导入时映射为英文枚举存储（建造师→CONSTRUCTOR）
        try (InputStream in = buildExcel(
                new BasicInfoRow("EMP001", "张三", "男", "2024-01-15", "13800000000"),
                new EduRow("EMP001", "清华大学", "2020-09", "2024-06", "本科", "全日制", "是"),
                new CertRow("EMP001", "一级建造师", "建造师", "高级", "否"))) {
            ImportResult result = importer.importFromStream(in);
            assertThat(result.certificateRows()).hasSize(1);
            assertThat(result.certificateRows().get(0).type()).isEqualTo("CONSTRUCTOR");
        }
    }

    @Test
    void map_all_chinese_certificate_types_to_english() throws IOException {
        // 覆盖所有 8 个证书类型中文→英文映射
        String[][] cases = {
                {"建造师", "CONSTRUCTOR"},
                {"PMP", "PMP"},
                {"工程师", "ENGINEER"},
                {"会计师", "ACCOUNTANT"},
                {"律师", "LAWYER"},
                {"安全工程师", "SECURITY"},
                {"IT类证书", "IT"},
                {"其他", "OTHER"}
        };
        for (String[] c : cases) {
            try (InputStream in = buildExcel(
                    new BasicInfoRow("EMP001", "张三", "男", "2024-01-15", "13800000000"),
                    new EduRow("EMP001", "清华大学", "2020-09", "2024-06", "本科", "全日制", "是"),
                    new CertRow("EMP001", "证书" + c[0], c[0], "高级", "否"))) {
                ImportResult result = importer.importFromStream(in);
                assertThat(result.certificateRows().get(0).type())
                        .as("证书类型 %s 应映射为 %s", c[0], c[1])
                        .isEqualTo(c[1]);
            }
        }
    }

    @Test
    void preserve_english_certificate_type_value_for_backward_compatibility() throws IOException {
        // 兼容性：英文枚举值（CONSTRUCTOR 等）仍能直接保留，不丢失
        try (InputStream in = buildExcel(
                new BasicInfoRow("EMP001", "张三", "男", "2024-01-15", "13800000000"),
                new EduRow("EMP001", "清华大学", "2020-09", "2024-06", "本科", "全日制", "是"),
                new CertRow("EMP001", "一级建造师", "CONSTRUCTOR", "高级", "否"))) {
            ImportResult result = importer.importFromStream(in);
            assertThat(result.certificateRows().get(0).type()).isEqualTo("CONSTRUCTOR");
        }
    }

    // ===== 测试数据 record =====

    private record BasicInfoRow(String employeeNumber, String name, String gender,
                                String entryDate, String phone) {}

    private record EduRow(String employeeNumber, String schoolName,
                          String startDate, String endDate,
                          String highestEducation, String studyForm,
                          String isHighestSchool) {}

    private record CertRow(String employeeNumber, String certificateName,
                           String type, String title, String isPermanent) {}
}
