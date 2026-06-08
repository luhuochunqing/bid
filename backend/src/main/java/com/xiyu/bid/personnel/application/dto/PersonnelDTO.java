package com.xiyu.bid.personnel.application.dto;

import com.xiyu.bid.personnel.domain.valueobject.PersonnelStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 人员 DTO（支持蓝图 4.3 新增证书子节的多条教育经历）
 */
public record PersonnelDTO(
        Long id,
        String name,
        String employeeNumber,
        String departmentCode,
        String departmentName,
        String gender,
        LocalDate entryDate,
        LocalDate birthDate,
        String phone,
        String education,                    // 旧单字段，兼容期保留
        String technicalTitle,
        PersonnelStatus status,
        String attachmentUrl,
        String remark,
        List<CertificateDTO> certificates,
        List<EducationDTO> educations,       // 新增：教育经历多条
        int totalProjects,
        // ===== 4.3 "查看证书" h5 计算字段（列表 11 列 + 详情所需） =====
        Integer yearsOfService,              // 入职年限（自动计算，entryDate 为空时为 null）
        String highestEducation,             // 最高学历 Tag（优先从 educations 动态计算）
        int certificateCount,                // 证书数量（可点击跳转证书 Tab）
        int expiringCertificatesCount,       // 即将到期证书数量（红色警示）
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public record EducationDTO(
            Long id,
            String schoolName,
            LocalDate startDate,
            LocalDate endDate,
            String highestEducation,
            String studyForm,
            String major,
            boolean isHighestEducationSchool
    ) {}
}
