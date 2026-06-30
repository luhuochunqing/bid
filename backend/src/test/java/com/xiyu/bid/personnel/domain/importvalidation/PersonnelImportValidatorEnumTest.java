package com.xiyu.bid.personnel.domain.importvalidation;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CO-419: 7 个枚举字段强校验（兜底，防止用户绕过模板下拉填入非法值）。
 *
 * 仅覆盖枚举值合法性校验；既有的工号、必填、跨 Sheet 一致性等逻辑由其他测试覆盖。
 */
class PersonnelImportValidatorEnumTest {

    private static ParsedPersonnelRow basicRow(String empNo, String name, String gender) {
        return new ParsedPersonnelRow(2, empNo, name, gender,
                LocalDate.of(2024, 1, 1), null, "13800000000",
                "本科", null, "研发部", null);
    }

    private static ParsedEducationRow eduRow(String empNo, String highest, String studyForm, String isHighestSchool) {
        return new ParsedEducationRow(3, empNo, "张三", "清华大学",
                LocalDate.of(2020, 9, 1), LocalDate.of(2024, 6, 30),
                highest, studyForm, "计算机", parseBool(isHighestSchool), isHighestSchool);
    }

    private static ParsedCertificateRow certRow(String empNo, String type, String title, String isPermanent) {
        return new ParsedCertificateRow(4, empNo, "张三", "一级建造师",
                "CERT001", type, LocalDate.of(2024, 1, 1), LocalDate.of(2027, 1, 1),
                "PER_张三_EMP001_01_一级建造师.pdf", title, parseBool(isPermanent), isPermanent, null);
    }

    private static Boolean parseBool(String val) {
        if (val == null || val.isBlank()) return null;
        return "是".equals(val) || "true".equalsIgnoreCase(val);
    }

    private static List<ImportValidationError> errorsOf(List<ParsedPersonnelRow> p,
                                                        List<ParsedEducationRow> e,
                                                        List<ParsedCertificateRow> c) {
        return PersonnelImportValidator.validate(p, e, c).errors();
    }

    // ===== 性别 =====

    @Test
    void gender_invalid_value_should_be_rejected() {
        List<ImportValidationError> errors = errorsOf(
                List.of(basicRow("EMP001", "张三", "未知")),
                List.of(), List.of());
        assertThat(errors).anyMatch(e -> "性别".equals(e.field()));
    }

    @Test
    void gender_valid_male_should_pass() {
        List<ImportValidationError> errors = errorsOf(
                List.of(basicRow("EMP001", "张三", "男")),
                List.of(), List.of());
        assertThat(errors).noneMatch(e -> "性别".equals(e.field()));
    }

    @Test
    void gender_blank_should_pass_as_optional() {
        List<ImportValidationError> errors = errorsOf(
                List.of(basicRow("EMP001", "张三", "")),
                List.of(), List.of());
        assertThat(errors).noneMatch(e -> "性别".equals(e.field()));
    }

    // ===== 最高学历 =====

    @Test
    void highestEducation_invalid_value_should_be_rejected() {
        List<ImportValidationError> errors = errorsOf(
                List.of(basicRow("EMP001", "张三", "男")),
                List.of(eduRow("EMP001", "幼儿园", "全日制", "是")),
                List.of());
        assertThat(errors).anyMatch(e -> "最高学历".equals(e.field()));
    }

    @Test
    void highestEducation_valid_value_should_pass() {
        List<ImportValidationError> errors = errorsOf(
                List.of(basicRow("EMP001", "张三", "男")),
                List.of(eduRow("EMP001", "本科", "全日制", "是")),
                List.of());
        assertThat(errors).noneMatch(e -> "最高学历".equals(e.field()));
    }

    // ===== 学习形式 =====

    @Test
    void studyForm_invalid_value_should_be_rejected() {
        List<ImportValidationError> errors = errorsOf(
                List.of(basicRow("EMP001", "张三", "男")),
                List.of(eduRow("EMP001", "本科", "自学", "是")),
                List.of());
        assertThat(errors).anyMatch(e -> "学习形式".equals(e.field()));
    }

    @Test
    void studyForm_valid_value_should_pass() {
        List<ImportValidationError> errors = errorsOf(
                List.of(basicRow("EMP001", "张三", "男")),
                List.of(eduRow("EMP001", "本科", "全日制", "是")),
                List.of());
        assertThat(errors).noneMatch(e -> "学习形式".equals(e.field()));
    }

    // ===== 是否为最高学历学校 =====

    @Test
    void isHighestEducationSchool_invalid_value_should_be_rejected() {
        List<ImportValidationError> errors = errorsOf(
                List.of(basicRow("EMP001", "张三", "男")),
                List.of(eduRow("EMP001", "本科", "全日制", "Y")),
                List.of());
        assertThat(errors).anyMatch(e -> "是否为最高学历学校".equals(e.field()));
    }

    @Test
    void isHighestEducationSchool_valid_yes_should_pass() {
        List<ImportValidationError> errors = errorsOf(
                List.of(basicRow("EMP001", "张三", "男")),
                List.of(eduRow("EMP001", "本科", "全日制", "是")),
                List.of());
        assertThat(errors).noneMatch(e -> "是否为最高学历学校".equals(e.field()));
    }

    // ===== 证书类型 =====
    // 注意：校验器校验的是导入器映射后的英文枚举值（CONSTRUCTOR 等），不是中文值。
    // 中文→英文映射在 PersonnelExcelImporter.mapCertificateType 中完成。

    @Test
    void certificateType_invalid_value_should_be_rejected() {
        List<ImportValidationError> errors = errorsOf(
                List.of(basicRow("EMP001", "张三", "男")),
                List.of(),
                List.of(certRow("EMP001", "法师", "高级", "否")));
        assertThat(errors).anyMatch(e -> "证书类型".equals(e.field()));
    }

    @Test
    void certificateType_valid_english_value_should_pass() {
        List<ImportValidationError> errors = errorsOf(
                List.of(basicRow("EMP001", "张三", "男")),
                List.of(),
                List.of(certRow("EMP001", "CONSTRUCTOR", "高级", "否")));
        assertThat(errors).noneMatch(e -> "证书类型".equals(e.field()));
    }

    // ===== 职称 =====

    @Test
    void certificateTitle_invalid_value_should_be_rejected() {
        List<ImportValidationError> errors = errorsOf(
                List.of(basicRow("EMP001", "张三", "男")),
                List.of(),
                List.of(certRow("EMP001", "建造师", "特级", "否")));
        assertThat(errors).anyMatch(e -> "职称".equals(e.field()));
    }

    @Test
    void certificateTitle_valid_value_should_pass() {
        List<ImportValidationError> errors = errorsOf(
                List.of(basicRow("EMP001", "张三", "男")),
                List.of(),
                List.of(certRow("EMP001", "建造师", "高级", "否")));
        assertThat(errors).noneMatch(e -> "职称".equals(e.field()));
    }

    // ===== 永久有效 =====

    @Test
    void isPermanent_invalid_value_should_be_rejected() {
        List<ImportValidationError> errors = errorsOf(
                List.of(basicRow("EMP001", "张三", "男")),
                List.of(),
                List.of(certRow("EMP001", "建造师", "高级", "对")));
        assertThat(errors).anyMatch(e -> "永久有效".equals(e.field()));
    }

    @Test
    void isPermanent_valid_yes_should_pass() {
        List<ImportValidationError> errors = errorsOf(
                List.of(basicRow("EMP001", "张三", "男")),
                List.of(),
                List.of(certRow("EMP001", "建造师", "高级", "是")));
        assertThat(errors).noneMatch(e -> "永久有效".equals(e.field()));
    }

    // ===== 整合：所有枚举合法时不应产生枚举相关错误 =====

    @Test
    void all_enums_valid_should_produce_no_enum_errors() {
        List<ImportValidationError> errors = errorsOf(
                List.of(basicRow("EMP001", "张三", "男")),
                List.of(eduRow("EMP001", "本科", "全日制", "是")),
                List.of(certRow("EMP001", "CONSTRUCTOR", "高级", "否")));
        assertThat(errors).isEmpty();
    }
}
