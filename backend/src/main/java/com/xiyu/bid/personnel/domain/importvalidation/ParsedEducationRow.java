package com.xiyu.bid.personnel.domain.importvalidation;

import java.time.LocalDate;

/**
 * 从 Excel Sheet2 解析后的一行教育经历（纯核心输入模型）
 *
 * CO-419: 新增 {@code rawIsHighestEducationSchool} 保留原始字符串，
 * 供 {@link PersonnelImportValidator} 校验枚举值合法性（"是/否"），
 * 避免被 {@code parseBoolCell} 提前归一化为 Boolean 后丢失非法值信号。
 */
public record ParsedEducationRow(
        Integer excelRow,
        String employeeNumber,
        String name,
        String schoolName,
        LocalDate startDate,
        LocalDate endDate,
        String highestEducation,
        String studyForm,
        String major,
        Boolean isHighestEducationSchool,
        String rawIsHighestEducationSchool
) {}
