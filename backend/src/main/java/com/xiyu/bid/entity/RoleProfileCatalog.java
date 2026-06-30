package com.xiyu.bid.entity;

/**
 * 投标系统角色目录——角色定义与 seed 的单一真相来源。
 *
 * <h3>角色定义（DEFINITIONS map）</h3>
 * 所有标准角色的 SeedDefinition 在此定义，包括角色 code、显示名、说明、dataScope 和菜单权限。
 *
 * <h3>角色 seed（{@link #seedDefinitions()}）</h3>
 * 返回的列表决定了哪些角色会在<b>系统初始化/bootstrap</b>时写入数据库 role_profile 表。
 * 只有在此列表中的角色才会计入「系统设置 → 角色权限」页面。
 *
 * <h3>角色数据来源链路</h3>
 * 角色数据可通过三种方式进入数据库：
 * <ol>
 *   <li><b>Seed</b>（{@link #seedDefinitions()}）：系统初始化/bootstrap 时写入。</li>
 *   <li><b>Flyway 迁移</b>：通过 db/migration-mysql/Vxxx 脚本写入。</li>
 *   <li><b>运维手动 INSERT</b>：直接操作数据库。</li>
 * </ol>
 * 通常一个角色只需在 seed 中定义即可，seed 与 DEFINITIONS 必须保持同步——
 * DEFINITIONS 中新增角色时必须在 seed 列表中加入，删除时必须从 seed 列表中移除。
 *
 * <h3>OSS 外部角色映射</h3>
 * 参见 {@code OrganizationUserSyncWriter.OSS_TO_INTERNAL_ROLE}。
 * 外部 OSS 角色 code（如 /bidAdmin、bid-TeamLeader）通过该映射表转换为内部 code。
 * 映射目标必须在 DEFINITIONS map 中（或是 admin/manager/staff 等系统内置 code）。
 */

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class RoleProfileCatalog {

    public static final String ADMIN_CODE = "admin";
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
    /** CO-403: 标讯查看权限点（审计日志等只读端点） */
    public static final String TENDER_VIEW_PERMISSION = "tender.view";
    /** CO-403: 人员库查看权限点（列表/详情只读） */
    public static final String PERSONNEL_VIEW_PERMISSION = "personnel.view";

    // PRD §2 角色：销售/业务负责人、投标负责人、投标部门管理员、任务执行人
    public static final String SALES_CODE = "bid-projectLeader";
    /** 语义别名：投标项目负责人（即 SALES_CODE），用于业务代码中更清晰地表达意图 */
    public static final String PROJECT_LEADER_CODE = SALES_CODE;
    public static final String BID_LEAD_CODE = "bid-TeamLeader";
    public static final String BID_ADMIN_CODE = "/bidAdmin";
    public static final String BID_SPECIALIST_CODE = "bid-Team";
    public static final String ADMIN_STAFF_CODE = "bid-administration";
    /** 跨部门协同人员：项目任务处理 */
    public static final String BID_OTHER_DEPT_CODE = "bid-otherDept";

    /** 拥有全局数据权限与操作权限的角色码集合。 */
    public static final Set<String> GLOBAL_ACCESS_ROLES = Set.of(ADMIN_CODE, BID_ADMIN_CODE, BID_LEAD_CODE);

    /** 不应继承 Legacy User.Role 鉴权兼容（ROLE_ADMIN/MANAGER）的新式受限角色。
     *  <p>这些角色仅靠自身 {@code ROLE_<CODE>} + 细粒度 menuPermissions 鉴权。若让它们继承
     *  MANAGER 兼容，会因 {@code hasAnyRole(... 'MANAGER' ...)} 类白名单误入本不应访问的模块。
     *  <p>其合法 API（如 {@code TaskController}）用 {@code isAuthenticated()}，移除 legacy 兼容不影响任务处理。
     *  <p>使用 case-insensitive TreeSet 以支持大小写不敏感查找。 */
    public static final Set<String> ROLES_WITHOUT_LEGACY_ROLE_COMPAT;
    static {
        java.util.TreeSet<String> set = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        set.add(BID_OTHER_DEPT_CODE);
        set.add(ADMIN_STAFF_CODE);
        set.add(BID_SPECIALIST_CODE);
        ROLES_WITHOUT_LEGACY_ROLE_COMPAT = Collections.unmodifiableSet(set);
    }

    /** 允许提交投标（推进至评标阶段）的业务角色码集合，对齐前端 useProjectDraftingPermissions.canSubmitBid。
     *  <p>语义划分：
     *  <ul>
     *    <li>{@link #SUBMIT_BID_DIRECT_ROLES}（admin/bid_admin/bid_lead）：直接放行</li>
     *    <li>{@link #SUBMIT_BID_LEAD_REQUIRED_ROLES}（sales/bid_specialist）：需在 ProjectDraftingService 中
     *        校验是否为该项目分配的负责人（sales→primaryLeadUserId，bid_specialist→secondaryLeadUserId）</li>
     *  </ul>
     *  <p>使用时优先用上述两个子集合；本集合由两者 union 派生，保持代数关系不变量。 */
    public static final Set<String> SUBMIT_BID_DIRECT_ROLES = Set.of(ADMIN_CODE, BID_ADMIN_CODE, BID_LEAD_CODE);

    /** 需项目级负责人分配才能提交投标的角色：sales→primaryLeadUserId，bid_specialist→secondaryLeadUserId。 */
    public static final Set<String> SUBMIT_BID_LEAD_REQUIRED_ROLES = Set.of(BID_SPECIALIST_CODE);

    /** 允许提交投标的全部角色 = {@link #SUBMIT_BID_DIRECT_ROLES} ∪ {@link #SUBMIT_BID_LEAD_REQUIRED_ROLES}。 */
    public static final Set<String> SUBMIT_BID_ALLOWED_ROLES = Stream.concat(
            SUBMIT_BID_DIRECT_ROLES.stream(),
            SUBMIT_BID_LEAD_REQUIRED_ROLES.stream()
    ).collect(Collectors.toUnmodifiableSet());

    /** 允许管理/审核项目任务的角色：投标管理员/组长/主管/投标专员(负责人/辅助)。对齐蓝图 §2.3.1。 */
    public static final Set<String> TASK_MUTATION_ALLOWED_ROLES =
            Set.of(ADMIN_CODE, BID_ADMIN_CODE, BID_LEAD_CODE, BID_SPECIALIST_CODE);

    /** 角色定义表，key 为角色 code。使用 case-insensitive TreeMap 以支持大小写不敏感查找
     *  （OSS 同步与本地 DB 可能传入不同大小写的 code）。 */
    private static final Map<String, SeedDefinition> DEFINITIONS;
    static {
        TreeMap<String, SeedDefinition> map = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        map.put(ADMIN_CODE, new SeedDefinition(ADMIN_CODE, "管理员", "系统管理员，拥有所有权限", true, "all", List.of("all")));
        map.put(SALES_CODE, new SeedDefinition(SALES_CODE, "投标项目负责人", "立项发起人，维护客户与开标信息", true, "self",
                List.of("dashboard", "bidding", "project",
                        "project.create", "project.view", "deposit.return.fill",
                        BIDDING_CREATE_PERMISSION,
                        // CO-393: 项目负责人需访问账户管理/CA信息管理（只读简化视图），需追加 resource 父权限及两个子权限
                        "resource", "resource-account", "resource-ca",
                        // CO-403: 项目负责人需只读访问标讯审计日志和人员库用于投标编制
                        TENDER_VIEW_PERMISSION, PERSONNEL_VIEW_PERMISSION,
                        "dashboard:view_welcome_banner", "dashboard:view_metric_cards", "dashboard:view_calendar",
                        "dashboard:view_tender_list", "dashboard:view_project_list", "dashboard:view_active_projects",
                        "dashboard:view_activity_list", "dashboard:view_priority_todos")));
        map.put(BID_LEAD_CODE, new SeedDefinition(BID_LEAD_CODE, "投标组长", "标书编制与评标推进负责人", true, "all",
                List.of("dashboard", "bidding", "project", "resource",
                        "task.assign", "evaluation.update", "result.register",
                        "retrospective.submit", "closure.request",
                        BIDDING_MANAGE_PERMISSION, BIDDING_CREATE_PERMISSION,
                        BIDDING_DELETE_PERMISSION,
                        BRAND_AUTH_VIEW_PERMISSION, BRAND_AUTH_CREATE_PERMISSION,
                        BRAND_AUTH_EDIT_PERMISSION, BRAND_AUTH_REVOKE_PERMISSION,
                        "knowledge-brand-auth",
                        TENDER_VIEW_PERMISSION, PERSONNEL_VIEW_PERMISSION,
                        "dashboard:view_welcome_banner", "dashboard:view_metric_cards", "dashboard:view_calendar",
                        "dashboard:view_tender_list", "dashboard:view_technical_task", "dashboard:view_review_list",
                        "dashboard:view_project_list", "dashboard:view_active_projects",
                        "dashboard:view_activity_list", "dashboard:view_priority_todos",
                        WAREHOUSE_MANAGE_PERMISSION)));
        map.put(BID_ADMIN_CODE, new SeedDefinition(BID_ADMIN_CODE, "投标管理员", "复盘审核与结项闸门审批", true, "all",
                List.of("dashboard", "operation-logs", "bidding", "project", "knowledge", "resource",
                        "analytics", "settings",
                        "task.review", "retrospective.submit", "retrospective.review", "closure.review", "lead.assign",
                        BIDDING_MANAGE_PERMISSION, BIDDING_CREATE_PERMISSION,
                        BIDDING_DELETE_PERMISSION, BIDDING_SYNC_PERMISSION,
                        BRAND_AUTH_VIEW_PERMISSION, BRAND_AUTH_CREATE_PERMISSION,
                        BRAND_AUTH_EDIT_PERMISSION, BRAND_AUTH_REVOKE_PERMISSION,
                        "knowledge-brand-auth",
                        TENDER_VIEW_PERMISSION, PERSONNEL_VIEW_PERMISSION,
                        "dashboard:view_welcome_banner", "dashboard:view_metric_cards", "dashboard:view_calendar",
                        "dashboard:view_tender_list", "dashboard:view_project_list", "dashboard:view_team_task",
                        "dashboard:view_global_projects", "dashboard:view_active_projects", "dashboard:view_team_performance",
                        "dashboard:view_approval_list", "dashboard:view_process_timeline", "dashboard:view_activity_list",
                        "dashboard:view_priority_todos",
                        WAREHOUSE_MANAGE_PERMISSION)));
        map.put(BID_SPECIALIST_CODE, new SeedDefinition(BID_SPECIALIST_CODE, "投标专员", "投标辅助、标书审核与任务处理", true, "self",
                List.of("dashboard", "bidding", "project", "resource",
                        "resource-ca", // CO-409: CA 信息管理模块访问（新增/批量导入对齐管理员，下架按保管员由 Service 校验）
                        "task.view.own", "task.handle.own", "evaluation.update",
                        "retrospective.submit",
                        BIDDING_CREATE_PERMISSION,
                        BRAND_AUTH_VIEW_PERMISSION, BRAND_AUTH_CREATE_PERMISSION,
                        BRAND_AUTH_EDIT_PERMISSION, "knowledge-brand-auth",
                        TENDER_VIEW_PERMISSION, PERSONNEL_VIEW_PERMISSION,
                        QUICK_START_PERMISSION, AI_CENTER_PERMISSION, "operation-logs",
                        "dashboard:view_welcome_banner", "dashboard:view_metric_cards", "dashboard:view_calendar",
                        "dashboard:view_tender_list", "dashboard:view_technical_task", "dashboard:view_active_projects",
                        "dashboard:view_activity_list", "dashboard:view_priority_todos",
                        WAREHOUSE_MANAGE_PERMISSION)));
        map.put(ADMIN_STAFF_CODE, new SeedDefinition(ADMIN_STAFF_CODE, "行政人员", "资质证书管理与行政事务", true, "self",
                List.of("certificate.manage", "qualification.view")));
        map.put(BID_OTHER_DEPT_CODE, new SeedDefinition(BID_OTHER_DEPT_CODE, "跨部门协同人员", "项目任务处理", true, "self",
                List.of("task.view.own", "task.handle.own",
                        "dashboard:view_welcome_banner", "dashboard:view_technical_task",
                        "dashboard:view_activity_list", "dashboard:view_priority_todos")));
        DEFINITIONS = Collections.unmodifiableSortedMap(map);
    }


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
                DEFINITIONS.get(ADMIN_STAFF_CODE)
        );
    }

    public static SeedDefinition definitionForCode(String roleCode) {
        if (roleCode == null) {
            return DEFINITIONS.get(ADMIN_CODE);
        }
        return DEFINITIONS.getOrDefault(roleCode.trim(), DEFINITIONS.get(ADMIN_CODE));
    }

    public static SeedDefinition definitionForLegacyRole(User.Role role) {
        if (role == null) {
            return DEFINITIONS.get(ADMIN_CODE);
        }
        return switch (role) {
            case ADMIN -> DEFINITIONS.get(ADMIN_CODE);
            case MANAGER -> DEFINITIONS.get(ADMIN_CODE);
        };
    }

    public static User.Role legacyRoleForCode(String roleCode) {
        String normalizedCode = roleCode == null ? "" : roleCode.trim();
        if (normalizedCode.equalsIgnoreCase(ADMIN_CODE) || normalizedCode.equalsIgnoreCase(BID_ADMIN_CODE)) {
            return User.Role.ADMIN;
        }
        return User.Role.MANAGER;
    }

    /** roleCode 是否为 catalog 已注册的标准角色。null/空白返回 false。 */
    public static boolean isRegisteredCode(String roleCode) {
        if (roleCode == null || roleCode.isBlank()) {
            return false;
        }
        return DEFINITIONS.containsKey(roleCode.trim());
    }

    /**
     * 返回 roleCode 的规范形式（来自 DEFINITIONS 的原始 key）。
     * <p>
     * 用于将大小写不一致的输入（如 {@code BidAdmin}、{@code BIDADMIN}）
     * 归一化为规范码（如 {@code bidAdmin}），避免下游权限匹配失败。
     * <p>
     * 未注册的 roleCode 返回 null。
     *
     * @param roleCode 待归一化的角色码
     * @return 规范角色码，未注册返回 null
     */
    public static String canonicalCode(String roleCode) {
        if (roleCode == null || roleCode.isBlank()) {
            return null;
        }
        String trimmed = roleCode.trim();
        // case-insensitive TreeMap 查找
        SeedDefinition def = DEFINITIONS.get(trimmed);
        return def == null ? null : def.code();
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
        String normalized = roleCode.trim();
        return ROLES_WITHOUT_LEGACY_ROLE_COMPAT.contains(normalized) || !DEFINITIONS.containsKey(normalized);
    }

    /**
     * 将角色码转换为 Spring Security authority 名称。
     * <p>
     * 规则：去除前导斜杠，连字符转下划线再大写。
     * <ul>
     *   <li>{@code /bidAdmin} → {@code BIDADMIN}</li>
     *   <li>{@code bid-TeamLeader} → {@code BID_TEAMLEADER}</li>
     *   <li>{@code bid-otherDept} → {@code BID_OTHERDEPT}</li>
     * </ul>
     * 用于统一 {@code @PreAuthorize} 中的 {@code hasRole()/hasAuthority()} 写法，
     * 避免各处手动 {@code replace("-", "_").toUpperCase()} 导致的不一致。
     *
     * @param roleCode 角色码（如 /bidAdmin）
     * @return authority 名称（如 BIDADMIN），null/空白返回 null
     */
    public static String toAuthorityName(String roleCode) {
        if (roleCode == null || roleCode.isBlank()) {
            return null;
        }
        // 去除前导斜杠（OSS 角色码如 /bidAdmin 中的 / 不是 authority 名的一部分）
        String normalized = roleCode.trim();
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (normalized.isEmpty()) {
            return null;
        }
        return normalized.replace("-", "_").toUpperCase(java.util.Locale.ROOT);
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
