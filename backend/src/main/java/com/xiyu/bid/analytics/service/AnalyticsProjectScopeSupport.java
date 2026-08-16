package com.xiyu.bid.analytics.service;

import com.xiyu.bid.service.ProjectAccessScopeService;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 数据分析模块项目级数据范围解析助手（P0 越权修复）。
 *
 * <p>全局范围角色（{@code GLOBAL_ACCESS_ROLES}：admin / /bidAdmin / bid-TeamLeader / bid-SystemAdmin）
 * 返回 {@code null}，表示全量可见；其余角色返回当前用户授权项目 ID 集合（可能为空集，表示无可见项目）。</p>
 *
 * <p>数据分析页面权限虽已限定为全局角色，本助手仍作为防御式兜底保留项目级过滤，
 * 统一复用 {@link ProjectAccessScopeService} 守卫，满足 ProjectAccessGuardCoverageTest 证据要求。</p>
 */
final class AnalyticsProjectScopeSupport {

    private AnalyticsProjectScopeSupport() {
    }

    static Set<Long> scopedProjectIds(ProjectAccessScopeService projectAccessScopeService) {
        if (projectAccessScopeService.currentUserHasGlobalAccess()) {
            return null;
        }
        List<Long> allowedIds = projectAccessScopeService.getAllowedProjectIdsForCurrentUser();
        if (allowedIds == null || allowedIds.isEmpty()) {
            return Set.of();
        }
        return allowedIds.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
