package com.xiyu.bid.personnel.domain.importvalidation;

import java.time.LocalDate;

/**
 * 从 Excel Sheet2 解析后的一行教育经历（纯核心输入模型）
 */
public record ParsedEducationRow(
        Integer excelRow,
        String employeeNumber,
        String name,                    // 用于交叉校验
        String schoolName,
        LocalDate startDate,
        LocalDate endDate,
        String highestEducation,
        String studyForm,
        String major
) {}
