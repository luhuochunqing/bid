package com.xiyu.bid.entity;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class RoleProfileCatalog {

    public static final String ADMIN_CODE = "admin";
    public static final String STAFF_CODE = "staff";
    public static final String QUICK_START_PERMISSION = "dashboard.quickStart";
    public static final String AI_CENTER_PERMISSION = "ai-center";
    public static final String BIDDING_MANAGE_PERMISSION = "bidding.manage";
    public static final String BIDDING_CREATE_PERMISSION = "bidding.create";
    public static final String BIDDING_DELETE_PERMISSION = "bidding.delete";
    public static final String BIDDING_SYNC_PERMISSION = "bidding.sync";
    public static final String WAREHOUSE_MANAGE_PERMISSION = "warehouse.manage";
    public static final String BRAND_AUTH_VIEW_PERMISSION = "brand-auth.view";
    public static final String BRAND_AUTH_CREATE_PERMISSION = "brand-auth.create";
    public static final String BRAND_AUTH_EDIT_PERMISSION = "brand-auth.edit";
    public static final String BRAND_AUTH_REVOKE_PERMISSION = "brand-auth.revoke";

    // PRD §2 角色：销售/业务负责人、投标负责人、投标部门管理员、任务执行人
    public static final String SALES_CODE = "sales";
    public static final String BID_LEAD_CODE = "bid_lead";
    public static final String BID_ADMIN_CODE = "bid_admin";
    public static final String BID_SPECIALIST_CODE = "bid_specialist";
    public static final String ADMIN_STAFF_CODE = "admin_staff";
    /** 跨部门协同人员：项目任务处理 */
    public static final String BID_OTHER_DEPT_CODE = "bid_other_dept";

    /** 拥有全局数据权限与操作权限的角色码集合。 */
    public static final Set<String> GLOBAL_ACCESS_ROLES = Set.of(ADMIN_CODE, BID_ADMIN_CODE, BID_LEAD_CODE);

    /** 不应继承 Legacy User.Role 鉴权兼容（ROLE_STAFF/ADMIN/MANAGER）的新式受限角色。
     *  <p>这些角色仅靠自身 {@code ROLE_<CODE>} + 细粒度 menuPermissions 鉴权。若让它们继承
     *  STAFF 兼容，会因 {@code hasAnyRole(... 'STAFF' ...)} 类白名单误入标讯/项目/知识库等
     *  STAFF 可见模块（如跨部门协同人员按蓝图不应访问标讯中心）。
     *  <p>其合法 API（如 {@code TaskController}）用 {@code isAuthenticated()}，移除 legacy 兼容不影响任务处理。 */
    public static final Set<String> ROLES_WITHOUT_LEGACY_ROLE_COMPAT = Set.of(BID_OTHER_DEPT_CODE);

    /** 允许提交投标（推进至评标阶段）的业务角色码集合，对齐前端 useProjectDraftingPermissions.canSubmitBid。 */
    public static final Set<String> SUBMIT_BID_ALLOWED_ROLES = Set.of(BID_ADMIN_CODE, BID_LEAD_CODE, SALES_CODE, BID_SPECIALIST_CODE);

    /** 允许管理/审核项目任务的角色：投标管理员/组长/主管/投标专员(负责人/辅助)。对齐蓝图 §2.3.1。 */
    public static final Set<String> TASK_MUTATION_ALLOWED_ROLES =
            Set.of(ADMIN_CODE, BID_ADMIN_CODE, BID_LEAD_CODE, BID_SPECIALIST_CODE);

    private static final Map<String, SeedDefinition> DEFINITIONS = Map.ofEntries(
            Map.entry(ADMIN_CODE, new SeedDefinition(ADMIN_CODE, "管理员", "系统管理员，拥有所有权限", true, "all", List.of("all"))),
                                                Map.entry(SALES_CODE, new SeedDefinition(SALES_CODE, "投标项目负责人", "立项发起人，维护客户与开标信息", true, "self",
                    List.of("dashboard", "bidding", "project",
                            "project.create", "project.view", "deposit.return.fill",
                            BIDDING_CREATE_PERMISSION,
                            "dashboard:view_welcome_banner", "dashboard:view_metric_cards", "dashboard:view_calendar",
                            "dashboard:view_tender_list", "dashboard:view_project_list", "dashboard:view_active_projects",
                            "dashboard:view_activity_list", "dashboard:view_priority_todos"))),
            Map.entry(BID_LEAD_CODE, new SeedDefinition(BID_LEAD_CODE, "投标组长", "标书编制与评标推进负责人", true, "all",
                    List.of("dashboard", "bidding", "project", "resource",
                            "task.assign", "evaluation.update", "result.register",
                            "retrospective.submit", "closure.request",
                            BIDDING_MANAGE_PERMISSION, BIDDING_CREATE_PERMISSION,
                            BIDDING_DELETE_PERMISSION,
                            BRAND_AUTH_VIEW_PERMISSION, BRAND_AUTH_CREATE_PERMISSION,
                            BRAND_AUTH_EDIT_PERMISSION, BRAND_AUTH_REVOKE_PERMISSION,
                            "knowledge-brand-auth",
                            "dashboard:view_welcome_banner", "dashboard:view_metric_cards", "dashboard:view_calendar",
                            "dashboard:view_tender_list", "dashboard:view_technical_task", "dashboard:view_review_list",
                            "dashboard:view_project_list", "dashboard:view_active_projects",
                            "dashboard:view_activity_list", "dashboard:view_priority_todos",
                            WAREHOUSE_MANAGE_PERMISSION))),
            Map.entry(BID_ADMIN_CODE, new SeedDefinition(BID_ADMIN_CODE, "投标管理员", "复盘审核与结项闸门审批", true, "all",
                    List.of("dashboard", "operation-logs", "bidding", "project", "knowledge", "resource",
                            "analytics", "settings",
                            "task.review", "retrospective.submit", "retrospective.review", "closure.review", "lead.assign",
                            BIDDING_MANAGE_PERMISSION, BIDDING_CREATE_PERMISSION,
                            BIDDING_DELETE_PERMISSION, BIDDING_SYNC_PERMISSION,
                            BRAND_AUTH_VIEW_PERMISSION, BRAND_AUTH_CREATE_PERMISSION,
                            BRAND_AUTH_EDIT_PERMISSION, BRAND_AUTH_REVOKE_PERMISSION,
                            "knowledge-brand-auth",
                            "dashboard:view_welcome_banner", "dashboard:view_metric_cards", "dashboard:view_calendar",
                            "dashboard:view_tender_list", "dashboard:view_project_list", "dashboard:view_team_task",
                            "dashboard:view_global_projects", "dashboard:view_active_projects", "dashboard:view_team_performance",
                            "dashboard:view_approval_list", "dashboard:view_process_timeline", "dashboard:view_activity_list",
                            "dashboard:view_priority_todos",
                            WAREHOUSE_MANAGE_PERMISSION))),
            Map.entry(BID_SPECIALIST_CODE, new SeedDefinition(BID_SPECIALIST_CODE, "投标专员", "投标辅助、标书审核与任务处理", true, "self",
                    List.of("dashboard", "bidding", "project", "resource",
                            "task.view.own", "task.handle.own", "evaluation.update",
                            "retrospective.submit",
                            BIDDING_CREATE_PERMISSION,
                            BRAND_AUTH_VIEW_PERMISSION, BRAND_AUTH_CREATE_PERMISSION,
                            BRAND_AUTH_EDIT_PERMISSION, "knowledge-brand-auth",
                            "dashboard:view_welcome_banner", "dashboard:view_metric_cards", "dashboard:view_calendar",
                            "dashboard:view_tender_list", "dashboard:view_technical_task", "dashboard:view_active_projects",
                            "dashboard:view_activity_list", "dashboard:view_priority_todos",
                            WAREHOUSE_MANAGE_PERMISSION))),
            Map.entry(ADMIN_STAFF_CODE, new SeedDefinition(ADMIN_STAFF_CODE, "行政人员", "资质证书管理与行政事务", true, "self",
                    List.of("certificate.manage", "qualification.view"))),
                        Map.entry(BID_OTHER_DEPT_CODE, new SeedDefinition(BID_OTHER_DEPT_CODE, "跨部门协同人员", "项目任务处理", true, "self",
                    List.of("task.view.own", "task.handle.own",
                            "dashboard:view_welcome_banner", "dashboard:view_technical_task",
                            "dashboard:view_activity_list", "dashboard:view_priority_todos")))
    );


    private RoleProfileCatalog() {
    }

    public static List<SeedDefinition> seedDefinitions() {
        return List.of(
                DEFINITIONS.get(ADMIN_CODE),
                DEFINITIONS.get(SALES_CODE),
                DEFINITIONS.get(BID_LEAD_CODE),
                DEFINITIONS.get(BID_ADMIN_CODE),
                DEFINITIONS.get(BID_SPECIALIST_CODE),
                DEFINITIONS.get(BID_OTHER_DEPT_CODE),
                DEFINITIONS.get(ADMIN_CODE)
        );
    }

    public static SeedDefinition definitionForCode(String roleCode) {
        if (roleCode == null) {
            return DEFINITIONS.get(ADMIN_CODE);
        }
        return DEFINITIONS.getOrDefault(roleCode.trim().toLowerCase(Locale.ROOT), DEFINITIONS.get(ADMIN_CODE));
    }

    public static SeedDefinition definitionForLegacyRole(User.Role role) {
        if (role == null) {
            return DEFINITIONS.get(ADMIN_CODE);
        }
        return switch (role) {
            case ADMIN -> DEFINITIONS.get(ADMIN_CODE);
            case MANAGER, STAFF -> DEFINITIONS.get(ADMIN_CODE);
        };
    }

    public static User.Role legacyRoleForCode(String roleCode) {
        String normalizedCode = roleCode == null ? STAFF_CODE : roleCode.trim().toLowerCase(Locale.ROOT);
        return switch (normalizedCode) {
            case ADMIN_CODE -> User.Role.ADMIN;
            case BID_ADMIN_CODE, BID_LEAD_CODE, SALES_CODE -> User.Role.MANAGER;
            default -> User.Role.STAFF;
        };
    }

    public static User.Role securityCompatLegacyRole(String roleCode) {
        String normalizedCode = roleCode == null ? STAFF_CODE : roleCode.trim().toLowerCase(Locale.ROOT);
        return switch (normalizedCode) {
            case ADMIN_CODE, BID_ADMIN_CODE -> User.Role.ADMIN;
            case BID_LEAD_CODE, SALES_CODE -> User.Role.MANAGER;
            default -> User.Role.STAFF;
        };
    }

    /** roleCode 是否为 catalog 已注册的标准角色。null/空白返回 false。 */
    public static boolean isRegisteredCode(String roleCode) {
        if (roleCode == null || roleCode.isBlank()) {
            return false;
        }
        return DEFINITIONS.containsKey(roleCode.trim().toLowerCase(Locale.ROOT));
    }

    /** 该 roleCode 是否应在颁发 Spring Security authority 时跳过 Legacy User.Role 兼容
     *  （即不发 {@code ROLE_STAFF/ADMIN/MANAGER}）。
     *  <p>命中条件（roleCode 非空时任一）：(1) 在 {@link #ROLES_WITHOUT_LEGACY_ROLE_COMPAT}，
     *  或 (2) 未在 catalog 注册（防御手动 INSERT 的角色误拿 STAFF fallback）。
     *  <p>roleCode 为 null/空白（纯 Legacy 用户）返回 false，保留其 {@code user.getRole()} 鉴权。 */
    public static boolean shouldSkipLegacyRoleCompat(String roleCode) {
        if (roleCode == null || roleCode.isBlank()) {
            return false;
        }
        String normalized = roleCode.trim().toLowerCase(Locale.ROOT);
        return ROLES_WITHOUT_LEGACY_ROLE_COMPAT.contains(normalized) || !DEFINITIONS.containsKey(normalized);
    }

    public record SeedDefinition(
            String code,
            String name,
            String description,
            boolean system,
            String dataScope,
            List<String> menuPermissions
    ) {
    }
}
