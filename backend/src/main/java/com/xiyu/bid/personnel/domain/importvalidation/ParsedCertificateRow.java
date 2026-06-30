package com.xiyu.bid.personnel.domain.importvalidation;

import java.time.LocalDate;

/**
 * 从 Excel Sheet3 解析后的一行证书信息（纯核心输入模型）
 *
 * CO-419: 新增 {@code rawIsPermanent} 保留原始字符串，
 * 供 {@link PersonnelImportValidator} 校验枚举值合法性（"是/否"），
 * 避免被 {@code parseBoolCell} 提前归一化为 Boolean 后丢失非法值信号。
 */
public record ParsedCertificateRow(
        Integer excelRow,
        String employeeNumber,
        String name,
        String certificateName,
        String certificateNumber,
        String type,
        LocalDate issueDate,
        LocalDate expiryDate,
        String attachmentFileName,
        String title,
        Boolean isPermanent,
        String rawIsPermanent,
        String remark
) {}
