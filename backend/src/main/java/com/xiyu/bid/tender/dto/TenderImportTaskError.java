package com.xiyu.bid.tender.dto;

/**
 * 标讯导入失败行明细（值对象）。
 * <p>作为 {@code TenderImportTask.error_details} JSON 数组元素的 Java 表示。
 *
 * @param rowNumber    Excel 行号（从 2 开始，1 是表头）
 * @param field        失败字段名（purchaserName/projectNo/duplicate/row）
 * @param errorMessage 错误描述（用户可读）
 * @param tenderTitle  标讯标题（用于用户定位是哪条标讯失败，可能为 null）
 */
public record TenderImportTaskError(
        int rowNumber,
        String field,
        String errorMessage,
        String tenderTitle
) {
}
