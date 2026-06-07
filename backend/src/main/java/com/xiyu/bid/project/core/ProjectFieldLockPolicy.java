package com.xiyu.bid.project.core;

import java.util.Objects;
import java.util.Set;

public final class ProjectFieldLockPolicy {
    private static final Set<String> INITIATION_LOCKED_FIELDS = Set.of("bidOpenTime", "ownerUnit");
    private ProjectFieldLockPolicy() {}

    public static Decision assertWritable(ProjectStage stage, String fieldName) {
        Objects.requireNonNull(stage, "stage 不能为空");
        if (fieldName == null || fieldName.isBlank()) return new Decision.Deny("fieldName 不能为空");
        if (stage == ProjectStage.CLOSED) return new Decision.Deny("项目已结项，全字段锁定");
        if (INITIATION_LOCKED_FIELDS.contains(fieldName)) return new Decision.Deny("提交后不可修改：" + fieldName);
        return Decision.ALLOW;
    }

    public static Decision assertInitiationWritable(boolean initiationApproved, String fieldName) {
        if (fieldName == null || fieldName.isBlank()) return new Decision.Deny("fieldName 不能为空");
        if (initiationApproved) return new Decision.Deny("项目立项已审核通过，信息不可编辑");
        return Decision.ALLOW;
    }

    public sealed interface Decision permits Decision.Allow, Decision.Deny {
        Decision ALLOW = new Allow();
        default boolean allowed() { return this instanceof Allow; }
        record Allow() implements Decision {}
        record Deny(String reason) implements Decision {}
    }
}
