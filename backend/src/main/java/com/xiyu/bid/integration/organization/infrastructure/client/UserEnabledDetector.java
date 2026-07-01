package com.xiyu.bid.integration.organization.infrastructure.client;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 用户在职判定（OSS 接口字段语义）。
 * <p>以 {@code /oauth/getUserInfo} 接口返回的 {@code status} 字段为最权威标志
 * （{@code status=1}=在职→启用，{@code status=0}=离职→关闭）。
 * <ul>
 *   <li>{@code del=1} → 已删除（最高优先级）</li>
 *   <li>{@code status=1} → 在职（覆盖 employeeStatus/activationState）</li>
 *   <li>{@code status=0} → 离职（覆盖 employeeStatus=3 在职判定）</li>
 *   <li>{@code employeeStatus=3} → 在职（status 缺失时 fallback）</li>
 *   <li>{@code employeeStatus=8/1} → 离职 / 待入职</li>
 *   <li>{@code activationState=1} → 已激活</li>
 *   <li>缺省 → 视为启用（避免漏同步）</li>
 * </ul>
 * <p>历史教训：曾用 YAPI mock 数据编写，把 {@code status==1} 当作启用条件；
 * 实际泊冉生产环境 {@code /oauth/getUserInfo} 接口的 status 语义为
 * {@code status=1=在职}、{@code status=0=离职}（与代码注释中的"反向"描述不同，
 * 已通过生产接口样本确认）。2026-06-30 调整为 status 最高优先级（仅次 del）。
 * <p>类型兼容：OSS 接口字段可能以字符串形态返回（{@code "status":"1"}），
 * 2026-07-01 放宽为数字与字符串双兼容（与 {@link OrganizationDirectoryJsonMapper#firstInt} 一致），
 * 避免 {@code isInt()} 严格判定导致 status 被跳过、误落到 fallback 造成状态反转。
 */
final class UserEnabledDetector {

    private UserEnabledDetector() {
    }

    static boolean isEnabled(JsonNode node) {
        // 1. del=1 明确=已删（最高优先级，已删除的用户不应被视为在职）
        Integer del = asIntOrNull(node.path("del"));
        if (del != null && del == 1) {
            return false;
        }
        // 2. status 字段为最高优先级判定（与 /oauth/getUserInfo 接口语义一致）
        //    status=1 → 在职（启用），status=0 → 离职（关闭）
        //    覆盖 employeeStatus/activationState，避免多字段语义冲突
        Integer status = asIntOrNull(node.path("status"));
        if (status != null) {
            return status == 1;
        }
        // 3. employeeStatus（status 缺失时 fallback）
        Integer employeeStatus = asIntOrNull(node.path("employeeStatus"));
        if (employeeStatus != null) {
            int es = employeeStatus;
            if (es == 3) {
                return true;   // 在职
            }
            if (es == 8 || es == 1) {
                return false;  // 离职 / 待入职
            }
            // 其他状态码：尝试 fallback
        }
        // 4. activationState=1 = 已激活
        Integer activationState = asIntOrNull(node.path("activationState"));
        if (activationState != null) {
            return activationState == 1;
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

    /**
     * 兼容数字与字符串两种形态解析为 int，无法解析返回 null。
     * <p>OSS 接口字段（status/del/employeeStatus/activationState）实测可能以字符串形态返回
     * （如 {@code "status":"1"}），与 {@link OrganizationDirectoryJsonMapper#firstInt} 保持一致的宽松口径。
     */
    private static Integer asIntOrNull(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return node.asInt();
        }
        if (node.isTextual()) {
            try {
                return Integer.parseInt(node.asText().trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
