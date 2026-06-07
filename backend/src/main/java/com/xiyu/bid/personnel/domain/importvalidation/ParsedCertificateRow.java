package com.xiyu.bid.personnel.domain.importvalidation;

import java.time.LocalDate;

/**
 * 从 Excel Sheet3 解析后的一行证书信息（纯核心输入模型）
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
        String attachmentFileName   // 原始文件名，供 AttachmentNameParser 进一步解析
) {}
