// checkstyle:off
package com.xiyu.bid.performance.application.command;

import java.time.LocalDate;
import java.util.List;

/**
 * 业绩搜索条件（蓝图 4.5）
 * 支持：关键词、客户类型多选、项目类型多选、合同状态多选、客户级别多选、
 *       属地、签约日期范围、截止日期范围、是否有中标通知书、项目负责人关键词
 */
public record PerformanceSearchCriteria(
        String keyword,
        List<String> customerTypes,
        List<String> projectTypes,
        List<String> statuses,
        List<String> customerLevels,
        String territory,
        LocalDate signingDateStart,
        LocalDate signingDateEnd,
        LocalDate expiryDateStart,
        LocalDate expiryDateEnd,
        Boolean hasBidNotice,
        String projectManagerKeyword
) {
    public static PerformanceSearchCriteria empty() {
        return new PerformanceSearchCriteria(
                null, null, null, null, null,
                null, null, null, null, null, null, null);
    }

    public static PerformanceSearchCriteria of(
            String keyword, List<String> customerTypes, List<String> projectTypes,
            List<String> statuses, List<String> customerLevels,
            String territory,
            LocalDate signingDateStart, LocalDate signingDateEnd,
            LocalDate expiryDateStart, LocalDate expiryDateEnd,
            Boolean hasBidNotice, String projectManagerKeyword) {
        return new PerformanceSearchCriteria(
                keyword, customerTypes, projectTypes, statuses, customerLevels,
                territory, signingDateStart, signingDateEnd,
                expiryDateStart, expiryDateEnd, hasBidNotice, projectManagerKeyword);
    }
}
