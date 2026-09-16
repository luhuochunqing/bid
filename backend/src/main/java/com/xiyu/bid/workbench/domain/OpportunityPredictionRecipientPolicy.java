// Input: OSS 实时岗位/角色回查结果 + 目标角色码集合
// Output: 账号当前可接收状态与目标角色命中结果
// Pos: workbench/domain - 商机预测通知接收人的纯规则
package com.xiyu.bid.workbench.domain;

import com.xiyu.bid.entity.RoleProfileCatalog;
import com.xiyu.bid.integration.organization.domain.policy.JobRoleLookupResolver;
import com.xiyu.bid.integration.organization.dto.OssUserJobAndRoleDto;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** 商机预测提醒的 OSS 实时状态与多角色判定。 */
public final class OpportunityPredictionRecipientPolicy {

    private OpportunityPredictionRecipientPolicy() {
    }

    /** 只有 OSS 明确返回在职/启用状态才允许接收，未知状态失败关闭。 */
    public static boolean isActive(OssUserJobAndRoleDto lookup) {
        if (lookup == null || lookup.status() == null) {
            return false;
        }
        return switch (lookup.status().trim().toLowerCase(Locale.ROOT)) {
            case "1", "true", "active", "enabled", "启用", "在职" -> true;
            default -> false;
        };
    }

    /** 判断 OSS 返回的任一活动角色码或角色名是否属于目标角色集合。 */
    public static boolean hasAnyTargetRole(OssUserJobAndRoleDto lookup, Set<String> targetRoleCodes) {
        if (lookup == null || lookup.sysRoleList() == null || targetRoleCodes == null
                || targetRoleCodes.isEmpty()) {
            return false;
        }
        Set<String> canonicalTargets = targetRoleCodes.stream()
                .filter(Objects::nonNull)
                .map(RoleProfileCatalog::canonicalCode)
                .filter(Objects::nonNull)
                .collect(Collectors.toUnmodifiableSet());
        return lookup.sysRoleList().stream()
                .map(JobRoleLookupResolver::mapOssRoleTextToInternal)
                .filter(Objects::nonNull)
                .anyMatch(canonicalTargets::contains);
    }
}
