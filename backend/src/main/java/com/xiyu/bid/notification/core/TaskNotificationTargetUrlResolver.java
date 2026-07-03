package com.xiyu.bid.notification.core;

import com.xiyu.bid.entity.RoleProfileCatalog;

/**
 * CO-474: 任务通知 targetUrl 解析策略 — 根据被分配人角色码决定通知跳转地址。
 *
 * <p>纯核心：无状态、无依赖、无副作用。所有方法静态、参数显式传入。
 * 不依赖 Spring、Repository 或任何 IO；可在单元测试中直接验证。
 *
 * <p>解析规则（对齐 ProjectNotificationService.notifyTaskAssigned 历史行为 + CO-474 修复）：
 * <ul>
 *   <li>跨部门协同人员（{@link RoleProfileCatalog#BID_OTHER_DEPT_CODE}）
 *       → {@code /task-board?taskId={taskId}&projectId={projectId}}（任务看板）</li>
 *   <li>其他角色（含 null/空/未匹配）→ {@code /project/{projectId}/drafting}（项目详情 drafting 阶段）</li>
 * </ul>
 *
 * <p>角色码来源：调用方（{@code ProjectNotificationService} / {@code TaskReviewNotificationService}）
 * 通过 {@link com.xiyu.bid.security.EffectiveRoleResolver#resolveRoleCode} 取得，格式统一为
 * {@link RoleProfileCatalog#BID_OTHER_DEPT_CODE} 常量定义的 mixedCase（{@code "bid-otherDept"}）。
 * 故本类使用 {@link String#equals} 精确比较即可，不做大小写不敏感防御。
 *
 * <p>供 ProjectNotificationService（任务分配通知）和 TaskReviewNotificationService（任务审核通知）
 * 共用，避免 targetUrl 角色判定逻辑在多个 Service 中复制。
 */
public final class TaskNotificationTargetUrlResolver {

    private TaskNotificationTargetUrlResolver() {
    }

    /**
     * 根据被分配人角色码解析任务通知的 targetUrl。
     *
     * <p>判定规则：
     * <ol>
     *   <li>roleCode 为 null/空白 → 兜底返回项目 drafting 页</li>
     *   <li>roleCode（去前后空格后）等于 {@link RoleProfileCatalog#BID_OTHER_DEPT_CODE}
     *       → 返回任务看板 URL {@code /task-board?taskId={taskId}&projectId={projectId}}</li>
     *   <li>其他 → 返回项目 drafting 页 {@code /project/{projectId}/drafting}</li>
     * </ol>
     *
     * @param projectId 项目 ID（必填，用于构造 URL 路径）
     * @param taskId    任务 ID（用于构造 task-board 跳转参数；bid-otherDept 角色下用到）
     * @param roleCode  当前被分配人角色码（可为 null/空），来自 EffectiveRoleResolver
     * @return 通知 targetUrl 字符串
     */
    public static String resolveTargetUrl(final Long projectId, final Long taskId, final String roleCode) {
        if (roleCode != null && !roleCode.isBlank()
                && RoleProfileCatalog.BID_OTHER_DEPT_CODE.equals(roleCode.trim())) {
            return "/task-board?taskId=" + taskId + "&projectId=" + projectId;
        }
        return "/project/" + projectId + "/drafting";
    }
}
