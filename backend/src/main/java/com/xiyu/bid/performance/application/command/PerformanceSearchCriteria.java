// checkstyle:off
package com.xiyu.bid.performance.application.command;

import java.time.LocalDate;

/**
 * 业绩搜索条件（蓝图 4.5）
 * 支持：关键词、客户类型、项目类型、合同状态、属地、
 *       签约日期范围、截止日期范围、是否有中标通知书、项目负责人关键词
 */
public record PerformanceSearchCriteria(
        String keyword,
        String customerType,
        String projectType,
        String status,
        // 蓝图新增筛选维度
        String territory,
        LocalDate signingDateStart,
        LocalDate signingDateEnd,
        LocalDate expiryDateStart,
        LocalDate expiryDateEnd,
        Boolean hasBidNotice,
        String projectManagerKeyword
) {
    public static PerformanceSearchCriteria of(
            String keyword, String customerType, String projectType, String status) {
        return new PerformanceSearchCriteria(
                keyword, customerType, projectType, status,
                null, null, null, null, null, null, null);
    }

    public static PerformanceSearchCriteria of(
            String keyword, String customerType, String projectType, String status,
            String territory,
            LocalDate signingDateStart, LocalDate signingDateEnd,
            LocalDate expiryDateStart, LocalDate expiryDateEnd,
            Boolean hasBidNotice, String projectManagerKeyword) {
        return new PerformanceSearchCriteria(
                keyword, customerType, projectType, status,
                territory, signingDateStart, signingDateEnd,
                expiryDateStart, expiryDateEnd, hasBidNotice, projectManagerKeyword);
    }
}
