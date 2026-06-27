package com.xiyu.bid.task.core;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * Pure core policy for task status transition validation.
 * No state, no dependencies, no side effects.
 */
public final class TaskTransitionPolicy {

    /** Allowed transitions from each status. */
    private static final Map<TaskStatus, Set<TaskStatus>>
            ALLOWED_TRANSITIONS;

    static {
        EnumMap<TaskStatus, Set<TaskStatus>> map =
                new EnumMap<>(TaskStatus.class);
        // CO-361 三态模型：TODO → REVIEW → COMPLETED（审核驳回回 TODO）
        // IN_PROGRESS / CANCELLED 已彻底废弃，策略层不再包含这两个状态
        // TODO 只能转 REVIEW
        map.put(TaskStatus.TODO,
                Set.of(TaskStatus.REVIEW));
        // REVIEW 可以前进到 COMPLETED，回退到 TODO（驳回；要求 reviewComment）
        map.put(TaskStatus.REVIEW,
                Set.of(TaskStatus.COMPLETED, TaskStatus.TODO));
        map.put(TaskStatus.COMPLETED, Set.of());
        ALLOWED_TRANSITIONS = Map.copyOf(map);
    }

    private TaskTransitionPolicy() {
    }

    /**
     * Validate whether a status transition is legal.
     *
     * @param current current task status
     * @param target  target task status
     * @return TransitionResult with allowed flag and reason
     */
    public static TransitionResult validateTransition(
            final TaskStatus current, final TaskStatus target) {
        if (current == null || target == null) {
            return TransitionResult.denied("状态不能为空");
        }
        if (current == target) {
            return TransitionResult.ok();
        }
        Set<TaskStatus> allowed =
                ALLOWED_TRANSITIONS.getOrDefault(current, Set.of());
        if (!allowed.contains(target)) {
            return TransitionResult.denied(
                    "不允许从 %s 切换到 %s"
                    + ", 合法目标: " + allowed);
        }
        return TransitionResult.ok();
    }

    /**
     * Validate transition with optional reviewComment.
     * PRD §3.2.2: REVIEW → TODO（退回待办）必须携带非空 reviewComment。
     *
     * @param current        current task status
     * @param target         target task status
     * @param reviewComment  reviewer comment (required for REVIEW→TODO rejection)
     * @return TransitionResult
     */
    public static TransitionResult validateTransition(
            final TaskStatus current, final TaskStatus target,
            final String reviewComment) {
        TransitionResult base = validateTransition(current, target);
        if (!base.allowed()) {
            return base;
        }
        if (current == TaskStatus.REVIEW && target == TaskStatus.TODO) {
            if (reviewComment == null || reviewComment.isBlank()) {
                return TransitionResult.denied(
                        "退回待办必须填写 reviewComment（驳回原因）");
            }
        }
        return TransitionResult.ok();
    }

    /**
     * Compute suggested auto-status when a deliverable is uploaded.
     * 业务规则：上传交付物不改变任务状态，保持 TODO 直到提交审核。
     *
     * @param current       current task status
     * @param existingCount number of existing deliverables
     * @return suggested status (always returns current status)
     */
    public static TaskStatus computeAutoStatusOnDeliverable(
            final TaskStatus current, final int existingCount) {
        if (current == null) {
            return TaskStatus.TODO;
        }
        return current;
    }

    /**
     * Result of a transition validation.
     *
     * @param allowed whether transition is legal
     * @param reason  human-readable explanation if denied
     */
    public record TransitionResult(boolean allowed, String reason) {

        /** Create an accepted result.
         *
         * @return accepted transition result
         */
        public static TransitionResult ok() {
            return new TransitionResult(true, "");
        }

        /** Create a denied result with reason.
         *
         * @param reason the denial reason
         * @return denied transition result
         */
        public static TransitionResult denied(final String reason) {
            return new TransitionResult(false, reason);
        }
    }

    /**
     * Status values matching Task.Status enum.
     * Core policy uses its own enum to avoid coupling to JPA entity.
     */
    public enum TaskStatus {
        /** Not started yet. */
        TODO,
        /** Pending review. */
        REVIEW,
        /** Fully completed. */
        COMPLETED
    }
}
