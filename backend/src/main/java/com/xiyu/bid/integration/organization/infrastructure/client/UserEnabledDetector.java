package com.xiyu.bid.integration.organization.infrastructure.client;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 用户在职判定（OSS /subscription/msg/getUserByTimeWindow 接口的字段语义）。
 * <ul>
 *   <li>{@code employeeStatus=3} → 在职（最权威标志）</li>
 *   <li>{@code employeeStatus=8} → 离职</li>
 *   <li>{@code employeeStatus=1} → 待入职（视为未启用）</li>
 *   <li>{@code del=1} → 已删除</li>
 *   <li>{@code status=1} → 在职（与 /oauth/getUserInfo 一致，但与 employeeStatus 反向）</li>
 *   <li>缺省 → 视为启用（避免漏同步）</li>
 * </ul>
 * <p>历史版本曾用 YAPI mock 数据编写，把 {@code status==1} 当作启用条件；
 * 实际泊冉生产环境主数据接口的 status 语义与 YAPI 相反（status=0=离职，status=1=在职），
 * 导致 99% 的同步用户被错误标记为 enabled=false。
 */
final class UserEnabledDetector {

    private UserEnabledDetector() {
    }

    static boolean isEnabled(JsonNode node) {
        // 1. employeeStatus 是最权威的在职标志
        JsonNode employeeStatus = node.path("employeeStatus");
        if (employeeStatus.isInt()) {
            int es = employeeStatus.asInt();
            if (es == 3) {
                return true;   // 在职
            }
            if (es == 8 || es == 1) {
                return false;  // 离职 / 待入职
            }
            // 其他状态码：尝试 fallback
        }
        // 2. del=1 明确=已删
        JsonNode del = node.path("del");
        if (del.isInt() && del.asInt() == 1) {
            return false;
        }
        // 3. activationState=1 = 已激活
        JsonNode activationState = node.path("activationState");
        if (activationState.isInt()) {
            return activationState.asInt() == 1;
        }
        // 4. status=1 在职（与 /oauth/getUserInfo 一致；和 employeeStatus=3 一一对应）
        JsonNode status = node.path("status");
        if (status.isInt()) {
            return status.asInt() == 1;
        }
        // 5. 显式 boolean 字段
        JsonNode enabled = node.path("enabled");
        if (enabled.isBoolean()) {
            return enabled.asBoolean();
        }
        JsonNode disabled = node.path("disabled");
        if (disabled.isBoolean()) {
            return !disabled.asBoolean();
        }
        // 6. 兜底：未明确禁用 = 视为启用
        return true;
    }
}
