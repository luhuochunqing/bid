// Input: 当前操作者角色 code + 当前用户 ID + 项目级投标负责人分配
// Output: Decision(allowed/reason) — 纯函数，无副作用
// Pos: project/core/ - pure core policy, no Spring/JPA
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.project.core;

import com.xiyu.bid.entity.RoleProfileCatalog;
import com.xiyu.bid.project.entity.ProjectLeadAssignment;

/**
 * 提交投标授权策略（蓝图 §3.3.1.2 权限矩阵 + CO-290 角色差异化）。
 * <p>纯核心：不依赖数据库、I/O、Spring 或日志。所有方法返回 {@link Decision} 值，
 * 编排层按 {@link Decision.Cause} 映射 HTTP 状态码（IDENTITY→403, STATE→409）。</p>
 *
 * <p>角色权限分层（对齐 {@link RoleProfileCatalog#SUBMIT_BID_ALLOWED_ROLES}）：</p>
 * <ul>
 *   <li>直接放行：{@code admin}/{@code bid_admin}/{@code bid_lead}</li>
 *   <li>需项目级负责人分配：
 *     <ul>
 *       <li>{@code sales} → 必须匹配 {@code ProjectLeadAssignment.primaryLeadUserId}</li>
 *       <li>{@code bid_specialist} → 必须匹配 {@code ProjectLeadAssignment.secondaryLeadUserId}</li>
 *     </ul>
 *   </li>
 *   <li>其他角色：直接拒绝</li>
 * </ul>
 */
public final class BidSubmissionAuthorizationPolicy {

    private BidSubmissionAuthorizationPolicy() {
    }

    /**
     * 校验当前用户是否可以提交投标（推进 DRAFTING → EVALUATING）。
     *
     * @param roleCode      当前操作者角色 code（已规范化小写，可为 null）
     * @param currentUserId 当前操作者用户 ID
     * @param lead          项目级投标负责人分配（可为 null 表示尚未分配）
     * @return 允许或拒绝决定
     */
    public static Decision canSubmitBid(String roleCode, Long currentUserId, ProjectLeadAssignment lead) {
        if (roleCode == null || !RoleProfileCatalog.SUBMIT_BID_ALLOWED_ROLES.contains(roleCode)) {
            return Decision.deny(Decision.Cause.IDENTITY, "当前角色无权限提交投标");
        }
        if (RoleProfileCatalog.SUBMIT_BID_DIRECT_ROLES.contains(roleCode)) {
            return Decision.permit();
        }
        if (lead == null) {
            return Decision.deny(Decision.Cause.IDENTITY, "项目尚未分配投标负责人，请联系管理员");
        }
        boolean matched;
        if (RoleProfileCatalog.SALES_CODE.equals(roleCode)) {
            // 投标项目负责人：仅匹配 primaryLeadUserId
            matched = lead.getPrimaryLeadUserId() != null
                    && lead.getPrimaryLeadUserId().equals(currentUserId);
        } else if (RoleProfileCatalog.BID_SPECIALIST_CODE.equals(roleCode)) {
            // bid_specialist 投标专员：仅匹配 secondaryLeadUserId
            matched = lead.getSecondaryLeadUserId() != null
                    && lead.getSecondaryLeadUserId().equals(currentUserId);
        } else {
            // 防御性兜底：SUBMIT_BID_LEAD_REQUIRED_ROLES 未来新增角色时未在此处补分支会显式拒绝
            return Decision.deny(Decision.Cause.IDENTITY, "当前角色无项目级负责人匹配规则");
        }
        return matched ? Decision.permit()
                : Decision.deny(Decision.Cause.IDENTITY, "您不是该项目的投标负责人，无权提交投标");
    }

    /**
     * 授权决策结果。
     *
     * <p>{@code cause} 供编排层映射 HTTP 状态码：</p>
     * <ul>
     *   <li>{@link Cause#STATE} → 409 Conflict</li>
     *   <li>{@link Cause#IDENTITY} → 403 Forbidden</li>
     * </ul>
     *
     * @param allowed 是否允许
     * @param cause   拒绝原因类型（allowed=true 时为 null）
     * @param reason  拒绝原因描述（allowed=true 时为 null）
     */
    public record Decision(boolean allowed, Cause cause, String reason) {

        public enum Cause {
            /** 资源状态机不允许该操作 */
            STATE,
            /** 操作者身份不符（角色无权 / 非项目指派负责人） */
            IDENTITY
        }

        public static Decision permit() {
            return new Decision(true, null, null);
        }

        public static Decision deny(Cause cause, String reason) {
            return new Decision(false, cause, reason);
        }
    }
}
