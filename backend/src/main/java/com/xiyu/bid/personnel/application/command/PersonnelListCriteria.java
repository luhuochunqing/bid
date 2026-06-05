package com.xiyu.bid.personnel.application.command;

import com.xiyu.bid.personnel.domain.valueobject.PersonnelStatus;

import java.time.LocalDate;
import java.util.List;

/**
 * 人员列表查询条件（已扩展支持「筛选与搜索」h5 的所有筛选项）
 */
public record PersonnelListCriteria(
        String keyword,                    // 姓名/工号模糊
        String departmentCode,
        PersonnelStatus status,            // 是否在职（ACTIVE / INACTIVE 等）
        String certificateType,            // 保留旧字段

        // === 新增筛选字段（筛选与搜索 h5） ===
        String gender,                     // 男 / 女
        List<String> highestEducations,    // 多选：本科、硕士等
        List<String> studyForms,           // 多选：全日制、非全日制等
        String majorKeyword,               // 专业模糊搜索
        LocalDate entryDateFrom,           // 入职时间范围起始
        LocalDate entryDateTo,             // 入职时间范围结束
        String certificateKeyword,         // 持有证书名称模糊（如"一级建造师"）
        List<String> certificateStatuses,  // 证书状态多选：VALID / EXPIRING / EXPIRED
        Boolean withExpiringCert           // 保留旧字段（兼容）
) {

    public static PersonnelListCriteria of(String keyword, String departmentCode,
            PersonnelStatus status, String certificateType, Boolean withExpiringCert) {
        return new PersonnelListCriteria(
                keyword, departmentCode, status, certificateType,
                null, null, null, null, null, null, null, null, withExpiringCert
        );
    }

    /**
     * 完整构造器（推荐在 Controller 中使用）
     */
    public static PersonnelListCriteria ofFull(
            String keyword, String departmentCode, PersonnelStatus status, String certificateType,
            String gender, List<String> highestEducations, List<String> studyForms, String majorKeyword,
            LocalDate entryDateFrom, LocalDate entryDateTo,
            String certificateKeyword, List<String> certificateStatuses, Boolean withExpiringCert
    ) {
        return new PersonnelListCriteria(
                keyword, departmentCode, status, certificateType,
                gender, highestEducations, studyForms, majorKeyword,
                entryDateFrom, entryDateTo, certificateKeyword, certificateStatuses, withExpiringCert
        );
    }
}
