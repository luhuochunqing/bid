package com.xiyu.bid.personnel.domain.model.importtask;

/**
 * 单条导入错误明细（纯核心值对象）
 */
public record ImportErrorDetail(
        String sheetName,
        Integer rowNumber,
        String employeeNumber,
        String name,
        String errorMessage
) {}
