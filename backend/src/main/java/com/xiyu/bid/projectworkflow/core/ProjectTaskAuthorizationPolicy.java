// Input: 当前角色 code + 任务状态 + 是否为任务指派人
// Output: Decision(allowed/reason) — 纯函数，无副作用
// Pos: projectworkflow/core/ - pure core policy, no Spring/JPA
package com.xiyu.bid.projectworkflow.core;

import com.xiyu.bid.entity.RoleProfileCatalog;

/**
 * 项目任务授权策略（飞书蓝图 §2.3.1 任务矩阵）。
 * <p>纯核心：不依赖数据库、I/O、Spring 或日志。所有方法返回 {@link Decision} 值，
 * 编排层按 {@link Decision.Cause} 映射 HTTP 状态码（IDENTITY→403, STATE→409）。</p>
 *
 * <p>角色口径（对齐审计 F1）：</p>
 * <ul>
 *   <li>管理/审核任务：{@code admin}/{@code bid_admin}/{@code bid_lead}/{@code bid_senior}/{@code bid_specialist}</li>
 *   <li>提交任务/上传交付物：任务执行人本人（角色无关，含 {@code bid_other_dept}/{@code sales}/{@code admin}）</li>
 * </ul>
 */
public final class ProjectTaskAuthorizationPolicy {

    private ProjectTaskAuthorizationPolicy() {
    }

    /**
     * 是否允许管理任务（手动添加任务 / AI 拆解 / 任务分配）。
     *
     * @param roleCode 当前操作者角色 code（已规范化小写）
     * @return 允许或拒绝决定
     */
    public static Decision canManageTask(String roleCode) {
        if (roleCode != null && RoleProfileCatalog.TASK_MUTATION_ALLOWED_ROLES.contains(roleCode)) {
            return Decision.permit();
        }
        return Decision.deny(Decision.Cause.IDENTITY, "当前角色无权管理项目任务");
    }

    /**
     * 是否允许提交任务 / 上传交付物（推进 TODO→REVIEW）。
     *
     * <p>身份约束：仅任务指派人本人可提交，与角色无关。</p>
     *
     * @param roleCode    当前操作者角色 code（保留入参对齐方法签名，本判定不依赖角色）
     * @param isAssignee  当前操作者是否为该任务的指派人
     * @return 允许或拒绝决定
     */
    public static Decision canSubmitTask(String roleCode, boolean isAssignee) {
        if (isAssignee) {
            return Decision.permit();
        }
        return Decision.deny(Decision.Cause.IDENTITY, "仅任务执行人本人可提交/上传交付物");
    }

    /**
     * 是否允许审核任务（REVIEW→COMPLETED 通过 / REVIEW→TODO 驳回）。
     *
     * @param roleCode 当前操作者角色 code
     * @return 允许或拒绝决定
     */
    public static Decision canReviewTask(String roleCode) {
        if (roleCode != null && RoleProfileCatalog.TASK_MUTATION_ALLOWED_ROLES.contains(roleCode)) {
            return Decision.permit();
        }
        return Decision.deny(Decision.Cause.IDENTITY, "当前角色无权审核任务");
    }

    /**
     * 任务状态流转授权路由。
     *
     * <p>纯核心路由：</p>
     * <ul>
     *   <li>from=REVIEW → {@link #canReviewTask}（审核通过/驳回，限管理员/组长/负责人/辅助）</li>
     *   <li>from=TODO 且 to=REVIEW → {@link #canSubmitTask}（执行人本人提交）</li>
     *   <li>其余转换 → {@link #canManageTask}（管理类操作）</li>
     * </ul>
     *
     * @param fromStatus  当前任务状态名（{@code Task.Status.name()}）
     * @param toStatus    目标任务状态名
     * @param roleCode    当前操作者角色 code
     * @param isAssignee  当前操作者是否为该任务的指派人
     * @return 允许或拒绝决定
     */
    public static Decision decideStatusTransition(
            String fromStatus,
            String toStatus,
            String roleCode,
            boolean isAssignee
    ) {
        if ("REVIEW".equals(fromStatus)) {
            return canReviewTask(roleCode);
        }
        if ("TODO".equals(fromStatus) && "REVIEW".equals(toStatus)) {
            return canSubmitTask(roleCode, isAssignee);
        }
        return canManageTask(roleCode);
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
            /** 操作者身份不符（角色无权 / 非任务指派人） */
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
