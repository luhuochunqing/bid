package com.xiyu.bid.entity;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class RoleProfileCatalog {

    public static final String ADMIN_CODE = "admin";
    public static final String AUDITOR_CODE = "auditor";
    public static final String MANAGER_CODE = "manager";
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
    public static final String TASK_EXECUTOR_CODE = "task_executor";
    public static final String BID_SPECIALIST_CODE = "bid_specialist";
    public static final String ADMIN_STAFF_CODE = "admin_staff";
    /** 投标主管：合并投标管理员(bid_admin) + 投标组长(bid_lead) 权限 */
    public static final String BID_SENIOR_CODE = "bid_senior";
    /** 跨部门协同人员：项目任务处理 */
    public static final String BID_OTHER_DEPT_CODE = "bid_other_dept";

    /** 拥有全局数据权限与操作权限的角色码集合。 */
    public static final Set<String> GLOBAL_ACCESS_ROLES = Set.of(ADMIN_CODE, BID_ADMIN_CODE, BID_LEAD_CODE, BID_SENIOR_CODE);

    /** 允许提交投标（推进至评标阶段）的业务角色码集合，对齐前端 useProjectDraftingPermissions.canSubmitBid。 */
    public static final Set<String> SUBMIT_BID_ALLOWED_ROLES = Set.of(BID_ADMIN_CODE, BID_LEAD_CODE, BID_SENIOR_CODE, SALES_CODE, BID_SPECIALIST_CODE);

    private static final Map<String, SeedDefinition> DEFINITIONS = Map.ofEntries(
            Map.entry(ADMIN_CODE, new SeedDefinition(ADMIN_CODE, "管理员", "系统管理员，拥有所有权限", true, "all", List.of("all"))),
            Map.entry(AUDITOR_CODE, new SeedDefinition(AUDITOR_CODE, "审计员", "审计人员，可查看全量审计日志和个人操作日志", true, "all",
                    List.of("dashboard", "operation-logs", "audit-logs",
                            "dashboard:view_welcome_banner", "dashboard:view_metric_cards"))),
            Map.entry(MANAGER_CODE, new SeedDefinition(MANAGER_CODE, "经理", "部门经理，可查看项目、知识库、资源与分析数据", true, "dept",
                    List.of("dashboard", "operation-logs", "bidding", "project", "knowledge", "resource",
                            AI_CENTER_PERMISSION, "analytics", "settings",
                            "dashboard:view_welcome_banner", "dashboard:view_metric_cards", "dashboard:view_calendar",
                            "dashboard:view_tender_list", "dashboard:view_technical_task", "dashboard:view_review_list",
                            "dashboard:view_customer_followup", "dashboard:view_project_list", "dashboard:view_team_task",
                            "dashboard:view_global_projects", "dashboard:view_active_projects", "dashboard:view_team_performance",
                            "dashboard:view_approval_list", "dashboard:view_process_timeline", "dashboard:view_activity_list",
                            "dashboard:view_priority_todos"))),
            Map.entry(STAFF_CODE, new SeedDefinition(STAFF_CODE, "员工", "业务人员，可查看工作台、标讯、项目、知识库与资源", true, "self",
                    List.of("dashboard", "operation-logs", QUICK_START_PERMISSION, "bidding", "project", "knowledge",
                            "resource", AI_CENTER_PERMISSION,
                            "dashboard:view_welcome_banner", "dashboard:view_metric_cards", "dashboard:view_calendar",
                            "dashboard:view_tender_list", "dashboard:view_technical_task", "dashboard:view_review_list",
                            "dashboard:view_customer_followup", "dashboard:view_project_list", "dashboard:view_active_projects",
                            "dashboard:view_process_timeline", "dashboard:view_activity_list", "dashboard:view_priority_todos"))),
            Map.entry(SALES_CODE, new SeedDefinition(SALES_CODE, "项目负责人", "立项发起人，维护客户与开标信息", true, "self",
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
            Map.entry(BID_ADMIN_CODE, new SeedDefinition(BID_ADMIN_CODE, "投标部门管理员", "复盘审核与结项闸门审批", true, "all",
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
            Map.entry(TASK_EXECUTOR_CODE, new SeedDefinition(TASK_EXECUTOR_CODE, "任务执行人", "标书任务承接与执行", true, "self",
                    List.of("dashboard", "project", "knowledge",
                            "task.view.own", "task.handle.own",
                            "dashboard:view_welcome_banner", "dashboard:view_calendar", "dashboard:view_technical_task",
                            "dashboard:view_active_projects", "dashboard:view_activity_list", "dashboard:view_priority_todos",
                            "evaluation.update"))),
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
            Map.entry(BID_SENIOR_CODE, new SeedDefinition(BID_SENIOR_CODE, "投标主管",
                    "投标组长+投标管理员合并角色：标书编制、评标推进、复盘审核与结项闸门审批", true, "all",
                    List.of("dashboard", "operation-logs", "bidding", "project", "knowledge", "resource",
                            "analytics", "settings",
                            "task.review", "task.assign", "evaluation.update", "result.register",
                            "retrospective.submit", "retrospective.review", "closure.review", "closure.request", "lead.assign",
                            BIDDING_MANAGE_PERMISSION, BIDDING_CREATE_PERMISSION,
                            BIDDING_DELETE_PERMISSION, BIDDING_SYNC_PERMISSION,
                            BRAND_AUTH_VIEW_PERMISSION, BRAND_AUTH_CREATE_PERMISSION,
                            BRAND_AUTH_EDIT_PERMISSION, BRAND_AUTH_REVOKE_PERMISSION,
                            "knowledge-brand-auth",
                            "dashboard:view_welcome_banner", "dashboard:view_metric_cards", "dashboard:view_calendar",
                            "dashboard:view_tender_list", "dashboard:view_project_list", "dashboard:view_team_task",
                            "dashboard:view_global_projects", "dashboard:view_active_projects", "dashboard:view_team_performance",
                            "dashboard:view_approval_list", "dashboard:view_process_timeline", "dashboard:view_activity_list",
                            "dashboard:view_priority_todos", "dashboard:view_technical_task", "dashboard:view_review_list",
                            WAREHOUSE_MANAGE_PERMISSION))),
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
                DEFINITIONS.get(AUDITOR_CODE),
                DEFINITIONS.get(MANAGER_CODE),
                DEFINITIONS.get(STAFF_CODE),
                DEFINITIONS.get(SALES_CODE),
                DEFINITIONS.get(BID_LEAD_CODE),
                DEFINITIONS.get(BID_ADMIN_CODE),
                DEFINITIONS.get(BID_SENIOR_CODE),
                DEFINITIONS.get(TASK_EXECUTOR_CODE),
                DEFINITIONS.get(BID_SPECIALIST_CODE),
                DEFINITIONS.get(BID_OTHER_DEPT_CODE),
                DEFINITIONS.get(ADMIN_STAFF_CODE)
        );
    }

    public static SeedDefinition definitionForCode(String roleCode) {
        if (roleCode == null) {
            return DEFINITIONS.get(STAFF_CODE);
        }
        return DEFINITIONS.getOrDefault(roleCode.trim().toLowerCase(Locale.ROOT), DEFINITIONS.get(STAFF_CODE));
    }

    public static SeedDefinition definitionForLegacyRole(User.Role role) {
        if (role == null) {
            return DEFINITIONS.get(STAFF_CODE);
        }
        return switch (role) {
            case ADMIN -> DEFINITIONS.get(ADMIN_CODE);
            case MANAGER -> DEFINITIONS.get(MANAGER_CODE);
            case STAFF -> DEFINITIONS.get(STAFF_CODE);
        };
    }

    public static User.Role legacyRoleForCode(String roleCode) {
        String normalizedCode = roleCode == null ? STAFF_CODE : roleCode.trim().toLowerCase(Locale.ROOT);
        return switch (normalizedCode) {
            case ADMIN_CODE -> User.Role.ADMIN;
            case MANAGER_CODE, BID_ADMIN_CODE, BID_LEAD_CODE, BID_SENIOR_CODE, SALES_CODE -> User.Role.MANAGER;
            default -> User.Role.STAFF;
        };
    }

    public static User.Role securityCompatLegacyRole(String roleCode) {
        String normalizedCode = roleCode == null ? STAFF_CODE : roleCode.trim().toLowerCase(Locale.ROOT);
        return switch (normalizedCode) {
            case ADMIN_CODE, BID_ADMIN_CODE -> User.Role.ADMIN;
            case MANAGER_CODE, BID_LEAD_CODE, BID_SENIOR_CODE, SALES_CODE -> User.Role.MANAGER;
            default -> User.Role.STAFF;
        };
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
