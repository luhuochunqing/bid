package com.xiyu.bid.personnel.domain.importvalidation;

/**
 * 单条导入校验警告（非阻塞，仅提示）
 * 典型场景：姓名与工号交叉不一致
 */
public record ImportValidationWarning(
        String sheet,
        Integer rowNumber,
        String employeeNumber,
        String message
) {
    public static ImportValidationWarning of(String sheet, Integer row, String empNo, String msg) {
        return new ImportValidationWarning(sheet, row, empNo, msg);
    }
}
