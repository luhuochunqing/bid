package com.xiyu.bid.personnel.domain.importvalidation;

/**
 * 单条导入校验错误（阻塞性）
 */
public record ImportValidationError(
        String sheet,           // "基础信息" / "教育经历" / "证书与职称"
        Integer rowNumber,      // Excel 行号（从1开始，含表头则+1）
        String employeeNumber,
        String field,           // 出错字段名
        String message          // 人类可读错误描述
) {
    public static ImportValidationError of(String sheet, Integer row, String empNo, String field, String msg) {
        return new ImportValidationError(sheet, row, empNo, field, msg);
    }
}
