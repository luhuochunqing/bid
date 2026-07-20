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
     * <p>姓名为空时 fallback 到 username，确保始终有姓名展示；
     * 工号取 {@link User#getDisplayEmployeeNumber()}（employee_number 为空时也 fallback 到 username）；
     * 两者都为空时只返回非空部分，避免出现"（）"。
     *
     * @param user 操作人，null 时返回空字符串
     */
    public static String format(User user) {
        if (user == null) {
            return "";
        }
        // fullName 为空时 fallback 到 username，确保始终有姓名展示
        String fullName = user.getFullName();
        if (fullName == null || fullName.isBlank()) {
            fullName = user.getUsername() != null ? user.getUsername() : "";
        }
        String employeeNumber = user.getDisplayEmployeeNumber();
        if (employeeNumber == null || employeeNumber.isBlank()) {
            return fullName;
        }
        if (fullName.isBlank()) {
            return employeeNumber;
        }
        return "%s（%s）".formatted(fullName, employeeNumber);
    }

    /**
     * 严格模式格式化：fullName 为空时返回空字符串（不回退到 username）。
     * <p>适用于调用方有自己的 fallback 逻辑（如返回原始 username、"未分配"等）的场景，
     * 避免出现"06234（06234）"这种姓名=工号的奇怪展示。
     * <p>调用方典型用法：
     * <pre>{@code
     * String display = OperatorDisplayName.formatStrict(user);
     * return display.isBlank() ? fallbackValue : display;
     * }</pre>
     *
     * @param user 操作人，null 或 fullName 为空时返回空字符串
     */
    public static String formatStrict(User user) {
        if (user == null) {
            return "";
        }
        String fullName = user.getFullName();
        if (fullName == null || fullName.isBlank()) {
            return "";
        }
        String employeeNumber = user.getDisplayEmployeeNumber();
        if (employeeNumber == null || employeeNumber.isBlank()) {
            return fullName;
        }
        return "%s（%s）".formatted(fullName, employeeNumber);
    }
}
