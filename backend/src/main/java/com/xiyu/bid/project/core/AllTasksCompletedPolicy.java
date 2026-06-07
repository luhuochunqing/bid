// Input: List<TaskState>（来自 shell 层的任务状态快照）
// Output: Allow | Deny{incompleteCount} - §3.2.3 推进到评标的任务全完成闸门
// Pos: project/core/ - 纯规则，无 Spring/JPA
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.project.core;

import java.util.List;

/**
 * PRD §3.2.3 DRAFTING → EVALUATING 闸门：要求所有任务都已终态完成。
 * <p>COMPLETED 与 CANCELLED 均视为"已离场"，非上述状态计为 incomplete。</p>
 */
public final class AllTasksCompletedPolicy {

    private AllTasksCompletedPolicy() {
    }

    public static Decision decide(List<TaskState> states) {
        if (states == null) {
            return new Decision.Deny(-1);
        }
        int incomplete = 0;
        for (TaskState s : states) {
            if (s == null || !(s == TaskState.COMPLETED || s == TaskState.CANCELLED)) {
                incomplete++;
            }
        }
        return incomplete == 0 ? Decision.ALLOW : new Decision.Deny(incomplete);
    }

    /** 任务终态集合（核心不依赖 JPA）。 */
    public enum TaskState {
        TODO, IN_PROGRESS, REVIEW, COMPLETED, CANCELLED
    }

    /** Sealed Decision: Allow | Deny{incompleteCount}. */
    public sealed interface Decision permits Decision.Allow, Decision.Deny {
        Decision ALLOW = new Allow();

        default boolean allowed() {
            return this instanceof Allow;
        }

        record Allow() implements Decision {
        }

        record Deny(int incompleteCount) implements Decision {
        }
    }
}
