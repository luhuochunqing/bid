package com.xiyu.bid.project.core;

import java.util.EnumSet;
import java.util.Set;

public final class EvaluationStateTransitionPolicy {
    private static final Set<EvaluationSubStage> VALID_STATES = EnumSet.allOf(EvaluationSubStage.class);
    private EvaluationStateTransitionPolicy() {}
    public static Decision decide(EvaluationSubStage current, EvaluationSubStage requested) {
        if (current == null || requested == null) return new Decision.Deny("不能为空");
        if (current == requested) return new Decision.Deny("不能切换到当前子状态");
        if (!VALID_STATES.contains(requested)) return new Decision.Deny("无效状态: " + requested);
        return Decision.ALLOW;
    }
    public sealed interface Decision permits Decision.Allow, Decision.Deny {
        Decision ALLOW = new Allow();
        default boolean allowed() { return this instanceof Allow; }
        record Allow() implements Decision {}
        record Deny(String reason) implements Decision {}
    }
}
