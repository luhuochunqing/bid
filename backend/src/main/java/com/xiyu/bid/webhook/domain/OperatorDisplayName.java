// Input: User 实体（fullName + displayEmployeeNumber）
// Output: "姓名（工号）" 格式的展示名
// Pos: webhook/domain/ - FP-Java 纯函数，不依赖 Spring
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.webhook.domain;

import com.xiyu.bid.entity.User;

/**
 * 操作人展示名格式化器（FP-Java 纯函数）。
 * <p>用于 §4.1 / §4.2 CRM 回调的 statusEditor / feedback.operator 字段，
 * 统一输出"姓名（工号）"格式，如"郑蓉蓉（06234）"。
 * <p>CO-346: 三个事件发布源（TenderEvaluationSubmissionService / TenderSubmissionService /
 * TenderEvaluationService）统一调用本工具，避免格式不一致。
 */
public final class OperatorDisplayName {

    private OperatorDisplayName() {}

    /**
     * 格式化为"姓名（工号）"。
     * <p>工号取 {@link User#getDisplayEmployeeNumber()}（employee_number 为空时 fallback 到 username）；
     * 姓名或工号为空时只返回非空部分，避免出现"（）"。
     *
     * @param user 操作人，null 时返回空字符串
     */
    public static String format(User user) {
        if (user == null) {
            return "";
        }
        String fullName = user.getFullName() != null ? user.getFullName() : "";
        String employeeNumber = user.getDisplayEmployeeNumber();
        if (employeeNumber == null || employeeNumber.isBlank()) {
            return fullName;
        }
        if (fullName.isEmpty()) {
            return employeeNumber;
        }
        return "%s（%s）".formatted(fullName, employeeNumber);
    }
}
