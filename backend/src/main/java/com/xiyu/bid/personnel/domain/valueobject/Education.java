package com.xiyu.bid.personnel.domain.valueobject;

import java.time.LocalDate;

/**
 * 教育经历值对象（对应蓝图 4.3 "新增证书" Tab 2 - 教育经历多条记录）
 * 纯核心，不可变，带基本业务不变式。
 */
public record Education(
        String schoolName,
        LocalDate startDate,
        LocalDate endDate,
        String highestEducation,
        String studyForm,
        String major,
        boolean isHighestEducationSchool
) {
    public Education {
        if (schoolName == null || schoolName.isBlank()) {
            throw new IllegalArgumentException("学校名称不能为空");
        }
        if (endDate == null) {
            throw new IllegalArgumentException("毕业时间不能为空");
        }
        // 入学时间为选填项；仅在两者都存在时校验先后顺序
        if (startDate != null && endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("毕业时间不能早于入学时间");
        }
    }
}
