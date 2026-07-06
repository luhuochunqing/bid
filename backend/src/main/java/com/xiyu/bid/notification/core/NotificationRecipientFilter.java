package com.xiyu.bid.notification.core;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Spec 030: 通知接收人过滤器 — 按资源可访问性过滤候选接收人。
 *
 * <p>纯核心：无状态、无依赖（仅 JDK）、无副作用。所有方法静态、参数显式传入。
 * 不依赖 Spring、Repository 或任何 IO；可在单元测试中直接验证。
 *
 * <p>用途：在通知派发前，从"按角色反查的候选接收人集合"中剔除"对该通知所属资源
 * 无访问权"的用户，避免被通知的用户点击跳转后被 403 拦截（spec 030 / 06131 案例）。
 *
 * <p>判定规则：
 * <ol>
 *   <li>候选集合为 null 或空 → 返回空列表（安全 no-op）</li>
 *   <li>候选元素为 null → 跳过（不传给 predicate）</li>
 *   <li>去重（基于 LinkedHashSet，保留输入顺序）</li>
 *   <li>对每个非 null 候选调用 {@code canAccessProject.test(uid)}，仅保留返回 true 的</li>
 *   <li>predicate 为 null → 抛 {@link NullPointerException}（调用方编程错误，早暴露）</li>
 *   <li>predicate 抛异常 → 向上透传（不在纯函数内吞错，调用方在 Service 层 try-catch 兜底）</li>
 * </ol>
 *
 * <p>"可访问性判定"通过 {@code Predicate<Long>} 参数注入，由调用方（Service 层）
 * 提供实际实现，通常为 {@code uid -> projectAccessScopeService.canAccessProject(uid, projectId)}。
 * 这样过滤器本身保持纯粹，不耦合 Spring 与 Repository。
 *
 * <p>详细契约见 specs/030-fix-task-review-notify-403/contracts/notification-filter-api.md。
 */
public final class NotificationRecipientFilter {

    private NotificationRecipientFilter() {
    }

    /**
     * 按资源可访问性过滤候选接收人。
     *
     * @param candidateUserIds  候选接收人 user_id 集合（可为 null 或空，返回空列表）
     * @param canAccessProject  可访问性判定函数（输入 user_id，返回 true 表示可访问目标资源）；
     *                          不可为 null，否则抛 {@link NullPointerException}
     * @return 过滤后的接收人 user_id 列表（保留候选集合的迭代顺序，已去重）；
     *         输入为 null/空或全部被过滤时返回空列表
     * @throws NullPointerException 如果 {@code canAccessProject} 为 null
     * @throws RuntimeException      如果 {@code canAccessProject.test(uid)} 抛异常（透传，不吞错）
     */
    public static List<Long> filterRecipients(
            final Collection<Long> candidateUserIds,
            final Predicate<Long> canAccessProject) {

        if (canAccessProject == null) {
            throw new NullPointerException("canAccessProject predicate must not be null");
        }
        if (candidateUserIds == null || candidateUserIds.isEmpty()) {
            return List.of();
        }

        // 去重 + 保序：LinkedHashSet 在 add 时去重，迭代顺序与插入顺序一致。
        LinkedHashSet<Long> seen = new LinkedHashSet<>();
        return candidateUserIds.stream()
                .filter(Objects::nonNull)
                .filter(seen::add)                       // 去重（首次出现保留）
                .filter(canAccessProject)                // 按可访问性过滤（predicate 异常透传）
                .toList();
    }
}
