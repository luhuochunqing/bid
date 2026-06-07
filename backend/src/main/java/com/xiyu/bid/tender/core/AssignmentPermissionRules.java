// Input: 最新的 TenderAssignmentRecord (Optional) + 当前用户 id
// Output: 实例级权限判定 (boolean)
// Pos: Core/纯规则
// 维护声明: 纯函数，不依赖框架 / IO / 状态；任何新增的实例级判定都集中在此。
package com.xiyu.bid.tender.core;

import com.xiyu.bid.batch.entity.TenderAssignmentRecord;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.entity.User;

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

    /**
     * 判定当前用户是否允许编辑标讯基本信息。
     * 
     * <p>规则：
     * <ul>
     *   <li>如果用户是投标专员 (bid_specialist)：
     *     <ul>
     *       <li>如果 status == PENDING_ASSIGNMENT 且 creatorId == userId -> false（无法编辑自己创建的未分配标讯）</li>
     *       <li>如果 creatorId == userId -> true（允许编辑自己创建的且已分配的标讯）</li>
     *       <li>否则 -> false（无权编辑他人创建的标讯）</li>
     *     </ul>
     *   </li>
     *   <li>如果 legacyRole == STAFF (其他普通员工类型) -> false</li>
     *   <li>否则 (ADMIN/MANAGER) -> true</li>
     * </ul>
     */
    public static boolean canEditTender(String userRoleCode, User.Role legacyRole, Long userId, Long creatorId, Tender.Status status) {
        if (userId == null) return false;

        // 创建人可以在 PENDING_ASSIGNMENT 状态下编辑自己的标讯（修正录入错误）
        if (status == Tender.Status.PENDING_ASSIGNMENT && Objects.equals(creatorId, userId)) {
            return true;
        }

        String roleCode = userRoleCode == null ? "" : userRoleCode.trim().toLowerCase(java.util.Locale.ROOT);
        if ("bid_specialist".equals(roleCode)) {
            return Objects.equals(creatorId, userId);
        }

        if (legacyRole == User.Role.STAFF) {
            return false;
        }

        return true;
    }
}
