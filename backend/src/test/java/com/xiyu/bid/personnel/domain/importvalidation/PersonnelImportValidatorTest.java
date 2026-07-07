package com.xiyu.bid.personnel.domain.importvalidation;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CO-528: 人员证书批量导入校验规则与新增（PersonnelValidator）对齐。
 *
 * 覆盖 PersonnelValidator 的 4 条规则在批量导入路径的等价实现：
 * - ENTRY_DATE_FUTURE: 入职日期 ≤ 今天
 * - BIRTH_DATE_INVALID: 入职 ≥ 出生+16 年
 * - EDUCATION_REQUIRED: 每人至少 1 条教育经历
 * - EDUCATION_DATE_INVALID: 毕业日期 ≥ 入学日期（已有，不在本类重复）
 *
 * 以及 CO-528 要求补齐的其他必填/格式校验：
 * - 教育必填字段（学校名称/毕业时间/最高学历/学习形式）
 * - 证书附件（填了名称则附件必填）
 * - 手机号格式（非空时 11 位数字）
 * - 性别必填
 */
class PersonnelImportValidatorTest {

    private static ParsedPersonnelRow basicRow(String empNo, String name, String gender,
                                                LocalDate entryDate, LocalDate birthDate,
                                                String phone) {
        return new ParsedPersonnelRow(2, empNo, name, gender,
                entryDate, birthDate, phone,
                "本科", null, "研发部", null);
    }

    private static ParsedEducationRow eduRow(String empNo, String schoolName,
                                             LocalDate startDate, LocalDate endDate,
                                             String highest, String studyForm) {
        return new ParsedEducationRow(3, empNo, "张三", schoolName,
                startDate, endDate,
                highest, studyForm, "计算机", true, "是");
    }

    private static ParsedCertificateRow certRow(String empNo, String certName, String attachment) {
        return new ParsedCertificateRow(4, empNo, "张三", certName,
                "CERT001", "CONSTRUCTOR", LocalDate.of(2024, 1, 1), LocalDate.of(2027, 1, 1),
                attachment, "高级", false, "否", null);
    }

    private static List<ImportValidationError> errorsOf(List<ParsedPersonnelRow> p,
                                                        List<ParsedEducationRow> e,
                                                        List<ParsedCertificateRow> c) {
        return PersonnelImportValidator.validate(p, e, c).errors();
    }

    // ===== 入职日期不能晚于今天 =====

    @Test
    void entryDate_future_should_be_rejected() {
        List<ImportValidationError> errors = errorsOf(
                List.of(basicRow("EMP001", "张三", "男",
                        LocalDate.now().plusDays(1), null, "13800000000")),
                List.of(), List.of());
        assertThat(errors).anyMatch(e -> "入职时间".equals(e.field())
                && e.message().contains("今天"));
    }

    @Test
    void entryDate_today_should_pass() {
        List<ImportValidationError> errors = errorsOf(
                List.of(basicRow("EMP001", "张三", "男",
                        LocalDate.now(), null, "13800000000")),
                List.of(), List.of());
        assertThat(errors).noneMatch(e -> "入职时间".equals(e.field()));
    }

    @Test
    void entryDate_null_should_pass_as_optional() {
        List<ImportValidationError> errors = errorsOf(
                List.of(basicRow("EMP001", "张三", "男",
                        null, null, "13800000000")),
                List.of(), List.of());
        assertThat(errors).noneMatch(e -> "入职时间".equals(e.field()));
    }

    // ===== 出生日期合理性：入职 ≥ 出生+16 年 =====

    @Test
    void birthDate_entryBefore16_should_be_rejected() {
        List<ImportValidationError> errors = errorsOf(
                List.of(basicRow("EMP001", "张三", "男",
                        LocalDate.of(2020, 1, 1),
                        LocalDate.of(2010, 1, 1), "13800000000")),
                List.of(), List.of());
        assertThat(errors).anyMatch(e -> "出生日期".equals(e.field()));
    }

    @Test
    void birthDate_entryAt16_should_pass() {
        List<ImportValidationError> errors = errorsOf(
                List.of(basicRow("EMP001", "张三", "男",
                        LocalDate.of(2026, 1, 1),
                        LocalDate.of(2010, 1, 1), "13800000000")),
                List.of(), List.of());
        assertThat(errors).noneMatch(e -> "出生日期".equals(e.field()));
    }

    @Test
    void birthDate_null_should_pass_as_optional() {
        List<ImportValidationError> errors = errorsOf(
                List.of(basicRow("EMP001", "张三", "男",
                        LocalDate.of(2024, 1, 1), null, "13800000000")),
                List.of(), List.of());
        assertThat(errors).noneMatch(e -> "出生日期".equals(e.field()));
    }

    // ===== 每人至少一条教育经历 =====

    @Test
    void noEducationRows_should_be_rejected() {
        List<ImportValidationError> errors = errorsOf(
                List.of(basicRow("EMP001", "张三", "男",
                        LocalDate.of(2024, 1, 1), null, "13800000000")),
                List.of(), List.of());
        assertThat(errors).anyMatch(e -> "教育经历".equals(e.field())
                && e.message().contains("至少"));
    }

    @Test
    void withEducationRow_should_pass_education_required() {
        List<ImportValidationError> errors = errorsOf(
                List.of(basicRow("EMP001", "张三", "男",
                        LocalDate.of(2024, 1, 1), null, "13800000000")),
                List.of(eduRow("EMP001", "清华大学",
                        LocalDate.of(2020, 9, 1), LocalDate.of(2024, 6, 30),
                        "本科", "全日制")),
                List.of());
        assertThat(errors).noneMatch(e -> "教育经历".equals(e.field())
                && e.message().contains("至少"));
    }

    // ===== 教育必填字段：学校名称 =====

    @Test
    void education_schoolName_blank_should_be_rejected() {
        List<ImportValidationError> errors = errorsOf(
                List.of(basicRow("EMP001", "张三", "男",
                        LocalDate.of(2024, 1, 1), null, "13800000000")),
                List.of(eduRow("EMP001", "",
                        LocalDate.of(2020, 9, 1), LocalDate.of(2024, 6, 30),
                        "本科", "全日制")),
                List.of());
        assertThat(errors).anyMatch(e -> "学校名称".equals(e.field()));
    }

    // ===== 教育必填字段：毕业时间 =====

    @Test
    void education_endDate_null_should_be_rejected() {
        List<ImportValidationError> errors = errorsOf(
                List.of(basicRow("EMP001", "张三", "男",
                        LocalDate.of(2024, 1, 1), null, "13800000000")),
                List.of(new ParsedEducationRow(3, "EMP001", "张三", "清华大学",
                        LocalDate.of(2020, 9, 1), null,
                        "本科", "全日制", "计算机", true, "是")),
                List.of());
        assertThat(errors).anyMatch(e -> "毕业时间".equals(e.field()));
    }

    // ===== 教育必填字段：最高学历 =====

    @Test
    void education_highestEducation_blank_should_be_rejected() {
        List<ImportValidationError> errors = errorsOf(
                List.of(basicRow("EMP001", "张三", "男",
                        LocalDate.of(2024, 1, 1), null, "13800000000")),
                List.of(eduRow("EMP001", "清华大学",
                        LocalDate.of(2020, 9, 1), LocalDate.of(2024, 6, 30),
                        "", "全日制")),
                List.of());
        assertThat(errors).anyMatch(e -> "最高学历".equals(e.field())
                && e.message().contains("必填"));
    }

    // ===== 教育必填字段：学习形式 =====

    @Test
    void education_studyForm_blank_should_be_rejected() {
        List<ImportValidationError> errors = errorsOf(
                List.of(basicRow("EMP001", "张三", "男",
                        LocalDate.of(2024, 1, 1), null, "13800000000")),
                List.of(eduRow("EMP001", "清华大学",
                        LocalDate.of(2020, 9, 1), LocalDate.of(2024, 6, 30),
                        "本科", "")),
                List.of());
        assertThat(errors).anyMatch(e -> "学习形式".equals(e.field())
                && e.message().contains("必填"));
    }

    // ===== 证书附件：填了名称则附件必填 =====

    @Test
    void certificate_nameWithoutAttachment_should_be_rejected() {
        List<ImportValidationError> errors = errorsOf(
                List.of(basicRow("EMP001", "张三", "男",
                        LocalDate.of(2024, 1, 1), null, "13800000000")),
                List.of(eduRow("EMP001", "清华大学",
                        LocalDate.of(2020, 9, 1), LocalDate.of(2024, 6, 30),
                        "本科", "全日制")),
                List.of(certRow("EMP001", "一级建造师", null)));
        assertThat(errors).anyMatch(e -> "附件".equals(e.field()));
    }

    @Test
    void certificate_nameWithAttachment_should_pass() {
        List<ImportValidationError> errors = errorsOf(
                List.of(basicRow("EMP001", "张三", "男",
                        LocalDate.of(2024, 1, 1), null, "13800000000")),
                List.of(eduRow("EMP001", "清华大学",
                        LocalDate.of(2020, 9, 1), LocalDate.of(2024, 6, 30),
                        "本科", "全日制")),
                List.of(certRow("EMP001", "一级建造师", "PER_张三_EMP001_01_一级建造师.pdf")));
        assertThat(errors).noneMatch(e -> "附件".equals(e.field()));
    }

    @Test
    void certificate_blankNameWithoutAttachment_should_pass() {
        List<ImportValidationError> errors = errorsOf(
                List.of(basicRow("EMP001", "张三", "男",
                        LocalDate.of(2024, 1, 1), null, "13800000000")),
                List.of(eduRow("EMP001", "清华大学",
                        LocalDate.of(2020, 9, 1), LocalDate.of(2024, 6, 30),
                        "本科", "全日制")),
                List.of(certRow("EMP001", "", null)));
        assertThat(errors).noneMatch(e -> "附件".equals(e.field()));
    }

    // ===== 手机号格式：非空时 11 位数字 =====

    @Test
    void phone_invalidLength_should_be_rejected() {
        List<ImportValidationError> errors = errorsOf(
                List.of(basicRow("EMP001", "张三", "男",
                        LocalDate.of(2024, 1, 1), null, "1380000000")),
                List.of(), List.of());
        assertThat(errors).anyMatch(e -> "手机号".equals(e.field()));
    }

    @Test
    void phone_nonDigit_should_be_rejected() {
        List<ImportValidationError> errors = errorsOf(
                List.of(basicRow("EMP001", "张三", "男",
                        LocalDate.of(2024, 1, 1), null, "1380000000a")),
                List.of(), List.of());
        assertThat(errors).anyMatch(e -> "手机号".equals(e.field()));
    }

    @Test
    void phone_blank_should_pass_as_optional() {
        List<ImportValidationError> errors = errorsOf(
                List.of(basicRow("EMP001", "张三", "男",
                        LocalDate.of(2024, 1, 1), null, "")),
                List.of(), List.of());
        assertThat(errors).noneMatch(e -> "手机号".equals(e.field()));
    }

    @Test
    void phone_valid_11_digits_should_pass() {
        List<ImportValidationError> errors = errorsOf(
                List.of(basicRow("EMP001", "张三", "男",
                        LocalDate.of(2024, 1, 1), null, "13800000000")),
                List.of(), List.of());
        assertThat(errors).noneMatch(e -> "手机号".equals(e.field()));
    }

    // ===== 性别必填（CO-528 对齐） =====

    @Test
    void gender_blank_should_be_rejected_as_required() {
        List<ImportValidationError> errors = errorsOf(
                List.of(basicRow("EMP001", "张三", "",
                        LocalDate.of(2024, 1, 1), null, "13800000000")),
                List.of(), List.of());
        assertThat(errors).anyMatch(e -> "性别".equals(e.field())
                && e.message().contains("必填"));
    }

    // ===== 整合：所有规则通过 =====

    @Test
    void all_rules_valid_should_produce_no_errors() {
        List<ImportValidationError> errors = errorsOf(
                List.of(basicRow("EMP001", "张三", "男",
                        LocalDate.of(2024, 1, 1),
                        LocalDate.of(1995, 1, 1), "13800000000")),
                List.of(eduRow("EMP001", "清华大学",
                        LocalDate.of(2020, 9, 1), LocalDate.of(2024, 6, 30),
                        "本科", "全日制")),
                List.of(certRow("EMP001", "一级建造师", "PER_张三_EMP001_01_一级建造师.pdf")));
        assertThat(errors).isEmpty();
    }
}
