package com.xiyu.bid.personnel.domain.importvalidation;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 人员证书批量导入校验器（纯核心）
 *
 * 职责：
 * - 单行字段校验（必填、格式、枚举、日期逻辑）
 * - 跨行/跨Sheet一致性校验（工号存在性、最高学历唯一性等）
 * - 姓名交叉校验（仅产生 Warning）
 *
 * 不负责：
 * - Excel 文件读取
 * - 数据库查询（工号是否存在由外壳在调用前准备好）
 * - 附件真实文件存在性检查
 */
public final class PersonnelImportValidator {

    private PersonnelImportValidator() {}

    /**
     * 对三张 Sheet 的原始数据进行完整校验。
     *
     * @param sheet1Rows 基础信息行（已按Excel顺序）
     * @param sheet2Rows 教育经历行
     * @param sheet3Rows 证书行
     */
    public static ValidationResult validate(
            List<ParsedPersonnelRow> sheet1Rows,
            List<ParsedEducationRow> sheet2Rows,
            List<ParsedCertificateRow> sheet3Rows
    ) {
        List<ImportValidationError> errors = new ArrayList<>();
        List<ImportValidationWarning> warnings = new ArrayList<>();

        // 1. Sheet1 基础校验 + 构建工号索引
        Set<String> validEmployeeNumbers = new HashSet<>();
        Map<String, String> empNoToName = new HashMap<>();

        for (int i = 0; i < sheet1Rows.size(); i++) {
            ParsedPersonnelRow row = sheet1Rows.get(i);
            int excelRow = i + 2; // 假设第1行是表头

            if (row.employeeNumber() == null || row.employeeNumber().isBlank()) {
                errors.add(ImportValidationError.of("基础信息", excelRow, null, "工号", "工号不能为空"));
                continue;
            }

            if (validEmployeeNumbers.contains(row.employeeNumber())) {
                errors.add(ImportValidationError.of("基础信息", excelRow, row.employeeNumber(), "工号", "工号重复"));
                continue;
            }
            validEmployeeNumbers.add(row.employeeNumber());
            empNoToName.put(row.employeeNumber(), row.name());

            // 其他基础字段校验（可继续扩展）
            if (row.name() == null || row.name().isBlank()) {
                errors.add(ImportValidationError.of("基础信息", excelRow, row.employeeNumber(), "姓名", "姓名不能为空"));
            }

            // CO-528: 性别必填（与 PersonnelValidator 对齐）
            if (!isNonBlank(row.gender())) {
                errors.add(ImportValidationError.of("基础信息", excelRow, row.employeeNumber(), "性别",
                        "性别必填"));
            } else if (!PersonnelImportEnumMapping.GENDER_VALUES.contains(row.gender())) {
                // CO-419: 性别枚举强校验（非空时必须在合法值集合内）
                errors.add(ImportValidationError.of("基础信息", excelRow, row.employeeNumber(), "性别",
                        "性别只能填：" + String.join("/", PersonnelImportEnumMapping.GENDER_DROPDOWN)));
            }

            // CO-528: 入职日期不能晚于今天（与 PersonnelValidator.ENTRY_DATE_FUTURE 对齐）
            if (row.entryDate() != null && row.entryDate().isAfter(LocalDate.now())) {
                errors.add(ImportValidationError.of("基础信息", excelRow, row.employeeNumber(), "入职时间",
                        "入职时间不能晚于今天"));
            }

            // CO-528: 出生日期合理性（与 PersonnelValidator.BIRTH_DATE_INVALID 对齐）
            if (row.birthDate() != null && row.entryDate() != null
                    && row.entryDate().isBefore(row.birthDate().plusYears(16))) {
                errors.add(ImportValidationError.of("基础信息", excelRow, row.employeeNumber(), "出生日期",
                        "出生日期不合理（入职时未满 16 岁）"));
            }

            // CO-528: 手机号格式（非空时 11 位数字）
            if (isNonBlank(row.phone()) && !row.phone().matches("\\d{11}")) {
                errors.add(ImportValidationError.of("基础信息", excelRow, row.employeeNumber(), "手机号",
                        "手机号必须为 11 位数字"));
            }
        }

        // 2. Sheet2 / Sheet3 工号存在性 + 交叉姓名校验
        Set<String> educationEmpNos = new HashSet<>();
        for (ParsedEducationRow row : sheet2Rows) {
            if (row.employeeNumber() == null || !validEmployeeNumbers.contains(row.employeeNumber())) {
                errors.add(ImportValidationError.of("教育经历", row.excelRow(), row.employeeNumber(), "工号",
                        "工号在【基础信息】中不存在"));
                continue;
            }
            educationEmpNos.add(row.employeeNumber());

            String expectedName = empNoToName.get(row.employeeNumber());
            if (row.name() != null && expectedName != null && !Objects.equals(row.name(), expectedName)) {
                warnings.add(ImportValidationWarning.of("教育经历", row.excelRow(), row.employeeNumber(),
                        "姓名与【基础信息】中的「" + expectedName + "」不一致（仅警告，不影响导入）"));
            }

            // 日期逻辑
            if (row.startDate() != null && row.endDate() != null && row.startDate().isAfter(row.endDate())) {
                errors.add(ImportValidationError.of("教育经历", row.excelRow(), row.employeeNumber(), "时间",
                        "入学时间不能晚于毕业时间"));
            }

            // CO-528: 教育必填字段校验（与新增路径对齐）
            if (!isNonBlank(row.schoolName())) {
                errors.add(ImportValidationError.of("教育经历", row.excelRow(), row.employeeNumber(), "学校名称",
                        "学校名称必填"));
            }
            if (row.endDate() == null) {
                errors.add(ImportValidationError.of("教育经历", row.excelRow(), row.employeeNumber(), "毕业时间",
                        "毕业时间必填"));
            }
            if (!isNonBlank(row.highestEducation())) {
                errors.add(ImportValidationError.of("教育经历", row.excelRow(), row.employeeNumber(), "最高学历",
                        "最高学历必填"));
            }
            if (!isNonBlank(row.studyForm())) {
                errors.add(ImportValidationError.of("教育经历", row.excelRow(), row.employeeNumber(), "学习形式",
                        "学习形式必填"));
            }

            // CO-419: 最高学历枚举强校验
            if (isNonBlank(row.highestEducation())
                    && !PersonnelImportEnumMapping.HIGHEST_EDUCATION_VALUES.contains(row.highestEducation())) {
                errors.add(ImportValidationError.of("教育经历", row.excelRow(), row.employeeNumber(), "最高学历",
                        "最高学历只能填：" + String.join("/", PersonnelImportEnumMapping.HIGHEST_EDUCATION_DROPDOWN)));
            }

            // CO-419: 学习形式枚举强校验
            if (isNonBlank(row.studyForm())
                    && !PersonnelImportEnumMapping.STUDY_FORM_VALUES.contains(row.studyForm())) {
                errors.add(ImportValidationError.of("教育经历", row.excelRow(), row.employeeNumber(), "学习形式",
                        "学习形式只能填：" + String.join("/", PersonnelImportEnumMapping.STUDY_FORM_DROPDOWN)));
            }

            // CO-419: 是否为最高学历学校枚举强校验（用 raw 字符串，避免 Boolean 归一化丢失非法值信号）
            if (isNonBlank(row.rawIsHighestEducationSchool())
                    && !PersonnelImportEnumMapping.YES_NO_VALUES.contains(row.rawIsHighestEducationSchool())) {
                errors.add(ImportValidationError.of("教育经历", row.excelRow(), row.employeeNumber(), "是否为最高学历学校",
                        "是否为最高学历学校只能填：" + String.join("/", PersonnelImportEnumMapping.YES_NO_DROPDOWN)));
            }
        }

        // CO-528: 每人至少一条教育经历（与 PersonnelValidator.EDUCATION_REQUIRED 对齐）
        Set<String> checkedEmpNosForEducation = new HashSet<>();
        for (ParsedPersonnelRow row : sheet1Rows) {
            String empNo = row.employeeNumber();
            if (empNo == null || empNo.isBlank()) continue;
            if (!validEmployeeNumbers.contains(empNo)) continue; // 跳过重复/无效工号
            if (checkedEmpNosForEducation.contains(empNo)) continue; // 同一工号只检查一次
            checkedEmpNosForEducation.add(empNo);
            if (!educationEmpNos.contains(empNo)) {
                errors.add(ImportValidationError.of("教育经历", row.excelRow(), empNo, "教育经历",
                        "每人至少需要一条教育经历"));
            }
        }

        for (ParsedCertificateRow row : sheet3Rows) {
            if (row.employeeNumber() == null || !validEmployeeNumbers.contains(row.employeeNumber())) {
                errors.add(ImportValidationError.of("证书与职称", row.excelRow(), row.employeeNumber(), "工号",
                        "工号在【基础信息】中不存在"));
                continue;
            }

            String expectedName = empNoToName.get(row.employeeNumber());
            if (row.name() != null && expectedName != null && !Objects.equals(row.name(), expectedName)) {
                warnings.add(ImportValidationWarning.of("证书与职称", row.excelRow(), row.employeeNumber(),
                        "姓名与【基础信息】中的「" + expectedName + "」不一致（仅警告）"));
            }

            // 证书有效期校验示例
            if (row.expiryDate() != null && row.issueDate() != null && row.expiryDate().isBefore(row.issueDate())) {
                errors.add(ImportValidationError.of("证书与职称", row.excelRow(), row.employeeNumber(), "有效期",
                        "有效期不能早于发证日期"));
            }

            // CO-528: 证书附件校验（填了证书名称则附件必填）
            if (isNonBlank(row.certificateName()) && !isNonBlank(row.attachmentFileName())) {
                errors.add(ImportValidationError.of("证书与职称", row.excelRow(), row.employeeNumber(), "附件",
                        "填写了证书名称后，附件必填"));
            }

            // CO-419: 证书类型枚举强校验（导入器已把中文映射为英文，校验英文枚举值）
            if (isNonBlank(row.type())
                    && !PersonnelImportEnumMapping.CERT_TYPE_EN_VALUES.contains(row.type())) {
                errors.add(ImportValidationError.of("证书与职称", row.excelRow(), row.employeeNumber(), "证书类型",
                        "证书类型只能填：" + String.join("/", PersonnelImportEnumMapping.CERT_TYPE_DROPDOWN)));
            }

            // CO-419: 职称枚举强校验
            if (isNonBlank(row.title())
                    && !PersonnelImportEnumMapping.CERT_TITLE_VALUES.contains(row.title())) {
                errors.add(ImportValidationError.of("证书与职称", row.excelRow(), row.employeeNumber(), "职称",
                        "职称只能填：" + String.join("/", PersonnelImportEnumMapping.CERT_TITLE_DROPDOWN)));
            }

            // CO-419: 永久有效枚举强校验（用 raw 字符串，避免 Boolean 归一化丢失非法值信号）
            if (isNonBlank(row.rawIsPermanent())
                    && !PersonnelImportEnumMapping.YES_NO_VALUES.contains(row.rawIsPermanent())) {
                errors.add(ImportValidationError.of("证书与职称", row.excelRow(), row.employeeNumber(), "永久有效",
                        "永久有效只能填：" + String.join("/", PersonnelImportEnumMapping.YES_NO_DROPDOWN)));
            }
        }

        // 3. 每人只能有一个“最高学历”（教育经历中）
        // （简化版，完整版可按工号分组后统计）
        // TODO: 后续增强

        return new ValidationResult(errors, warnings);
    }

    private static boolean isNonBlank(String val) {
        return val != null && !val.isBlank();
    }
}
