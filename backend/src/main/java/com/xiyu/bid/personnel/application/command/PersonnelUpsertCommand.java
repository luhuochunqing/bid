package com.xiyu.bid.personnel.application.command;

import com.xiyu.bid.personnel.domain.valueobject.CertificateType;

import java.time.LocalDate;
import java.util.List;

/**
 * 新增/编辑人员命令（支持蓝图 4.3 "新增证书" 子节的多条教育经历）
 */
public record PersonnelUpsertCommand(
        String name,
        String employeeNumber,
        String departmentCode,
        String departmentName,
        String gender,
        java.time.LocalDate entryDate,
        java.time.LocalDate birthDate,
        String phone,
        String education,                    // 保留旧字段，向后兼容本迭代
        String technicalTitle,
        String attachmentUrl,
        String remark,
        List<CertificateEntry> certificates,
        List<EducationEntry> educations      // 新增：教育经历多条（Tab 2）
) {
    public record CertificateEntry(
            String name,
            String certificateNumber,
            CertificateType type,
            LocalDate issueDate,
            LocalDate expiryDate,
            String attachmentUrl,
            String title,
            boolean isPermanent,
            String remark
    ) {}

    public record EducationEntry(
            String schoolName,
            LocalDate startDate,
            LocalDate endDate,
            String highestEducation,
            String studyForm,
            String major,
            boolean isHighestEducationSchool
    ) {}
}
