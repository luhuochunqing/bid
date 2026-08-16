package com.xiyu.bid.entity;

import java.util.List;

/**
 * /bidAdmin 和 bid-SystemAdmin 共享的菜单权限清单（权限等同投标管理员）。
 * <p>从 {@link RoleProfileCatalog} 抽出，避免主类超过 300 行 FP-Java 行预算。
 * 仅在 {@link RoleProfileCatalog} 初始化 {@code SeedDefinition} 时使用。</p>
 */
final class RoleProfileAdminPermissions {

    private RoleProfileAdminPermissions() {
    }

    /** /bidAdmin 与 bid-SystemAdmin 共享的菜单权限清单。 */
    static final List<String> LIST = List.of(
            "dashboard", "operation-logs", "bidding", "project", "knowledge", "resource",
            "analytics", "settings", "settings-alerts",
            // 数据分析页面（/analytics/dashboard）：仅 3 个全局角色持有，与 catalog 口径一致
            RoleProfileCatalog.ANALYTICS_DASHBOARD_PERMISSION,
            "task.review", "retrospective.submit", "retrospective.review", "closure.review", "lead.assign",
            RoleProfileCatalog.BIDDING_MANAGE_PERMISSION, RoleProfileCatalog.BIDDING_CREATE_PERMISSION,
            RoleProfileCatalog.BIDDING_DELETE_PERMISSION, RoleProfileCatalog.BIDDING_SYNC_PERMISSION,
            RoleProfileCatalog.BRAND_AUTH_VIEW_PERMISSION, RoleProfileCatalog.BRAND_AUTH_CREATE_PERMISSION,
            RoleProfileCatalog.BRAND_AUTH_EDIT_PERMISSION, RoleProfileCatalog.BRAND_AUTH_REVOKE_PERMISSION,
            "knowledge-brand-auth",
            RoleProfileCatalog.TENDER_VIEW_PERMISSION, RoleProfileCatalog.PERSONNEL_VIEW_PERMISSION, RoleProfileCatalog.PERSONNEL_MANAGE_PERMISSION,
            RoleProfileCatalog.PERFORMANCE_MANAGE_PERMISSION, RoleProfileCatalog.QUALIFICATION_MANAGE_PERMISSION,
            RoleProfileCatalog.QUALIFICATION_VIEW_PERMISSION, RoleProfileCatalog.KNOWLEDGE_QUALIFICATION_PERMISSION,
            RoleProfileCatalog.KNOWLEDGE_PERSONNEL_PERMISSION,
            RoleProfileCatalog.KNOWLEDGE_ARCHIVE_PERMISSION, RoleProfileCatalog.KNOWLEDGE_CASE_PERMISSION, RoleProfileCatalog.KNOWLEDGE_TEMPLATE_PERMISSION,
            RoleProfileCatalog.KNOWLEDGE_WAREHOUSE_PERMISSION, RoleProfileCatalog.KNOWLEDGE_PERFORMANCE_PERMISSION,
            "dashboard:view_welcome_banner", "dashboard:view_metric_cards", "dashboard:view_calendar",
            "dashboard:view_tender_list", "dashboard:view_project_list", "dashboard:view_team_task",
            "dashboard:view_global_projects", "dashboard:view_active_projects", "dashboard:view_team_performance",
            "dashboard:view_approval_list", "dashboard:view_process_timeline", "dashboard:view_activity_list",
            "dashboard:view_priority_todos",
            RoleProfileCatalog.WAREHOUSE_MANAGE_PERMISSION);
}
