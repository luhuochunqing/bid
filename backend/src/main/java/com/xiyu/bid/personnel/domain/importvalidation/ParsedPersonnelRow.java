package com.xiyu.bid.personnel.domain.importvalidation;

import java.time.LocalDate;

/**
 * 从 Excel Sheet1 解析后的一行基础信息（纯核心输入模型）
 */
public record ParsedPersonnelRow(
        Integer excelRow,
        String employeeNumber,
        String name,
        String gender,
        LocalDate entryDate,
        LocalDate birthDate,
        String phone,
        String education,
        String technicalTitle
) {}
