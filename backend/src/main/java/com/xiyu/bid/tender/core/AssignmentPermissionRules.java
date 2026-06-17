// Input: 最新的 TenderAssignmentRecord (Optional) + 当前用户 id
// Output: 实例级权限判定 (boolean)
// Pos: Core/纯规则
// 维护声明: 纯函数，不依赖框架 / IO / 状态；任何新增的实例级判定都集中在此。
package com.xiyu.bid.tender.core;

import com.xiyu.bid.batch.entity.TenderAssignmentRecord;

import java.util.Objects;
import java.util.Optional;

/**
 * 标讯实例级权限的纯规则。
 *
 * <p>仅做值到值的判定：给定 latest assignment 记录与用户 id，返回该用户是否
 * 具备 fill / decide 权限。
 *
 * <p>无 IO、无副作用、无 Spring 注入 — 真单元。
 */
public final class AssignmentPermissionRules {

    private AssignmentPermissionRules() {}

    /** 用户为 latest assignee（被分配的项目经理） → 可填评估表。 */
    public static boolean canFill(Optional<TenderAssignmentRecord> latest, Long userId) {
        if (userId == null) return false;
        return latest.map(r -> Objects.equals(r.getAssigneeId(), userId)).orElse(false);
    }

    /** 用户为 latest assigned-by（分配标讯的人） → 可投标 / 弃标。 */
    public static boolean canDecide(Optional<TenderAssignmentRecord> latest, Long userId) {
        if (userId == null) return false;
        return latest
                .map(r -> r.getAssignedById() != null && Objects.equals(r.getAssignedById(), userId))
                .orElse(false);
    }

}
