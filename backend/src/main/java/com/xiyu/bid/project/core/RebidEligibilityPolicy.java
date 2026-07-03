// Input: 项目阶段 (ProjectStage)
// Output: Allow / Deny(reason) sealed Decision; 二次招标资格判断
// Pos: project/core/ - pure rule, no Spring/JPA
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.project.core;

import java.util.Objects;

/**
 * 二次招标资格策略。产品蓝图 §4.3：
 * <ul>
 *   <li>项目阶段为 CLOSED → Allow（流标直通 CLOSED + 结项审批后 CLOSED 都覆盖）。</li>
 *   <li>其他阶段 → Deny("项目尚未结项，无法二次招标")。</li>
 * </ul>
 */
public final class RebidEligibilityPolicy {

    private RebidEligibilityPolicy() {}

    public static Decision decide(ProjectStage stage) {
        Objects.requireNonNull(stage, "stage 不能为空");
        if (stage == ProjectStage.CLOSED) {
            return Decision.ALLOW;
        }
        return new Decision.Deny("项目尚未结项，无法二次招标");
    }

    public sealed interface Decision permits Decision.Allow, Decision.Deny {
        Decision ALLOW = new Allow();
        boolean allowed();

        record Allow() implements Decision {
            @Override
            public boolean allowed() { return true; }
        }

        record Deny(String reason) implements Decision {
            @Override
            public boolean allowed() { return false; }
        }
    }
}
