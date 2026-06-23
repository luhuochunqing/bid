package com.xiyu.bid.resources.service;

import com.xiyu.bid.entity.RoleProfileCatalog;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * Role-based visibility policy for margin ledger queries.
 *
 * <p>This enum replaces raw {@code String} role values passed to
 * {@link MarginQuerySupport#appendRole}, eliminating the risk that an
 * attacker-controlled role string reaches SQL concatenation.</p>
 *
 * <p>Each constant knows the exact SQL fragment it contributes. Unknown roles
 * fall back to the most restrictive scope (staff / team member visibility).</p>
 *
 * <p>枚举名与 {@link RoleProfileCatalog} 中的角色码常量名对齐，
 * LOOKUP 表从 Catalog 派生，避免角色码变更时遗漏同步。</p>
 */
enum MarginQueryRole {

    ADMIN((pa, pi) -> ""),
    MANAGER((pa, pi) -> ""),
    STAFF(MarginQueryRole::staffFragment),
    BID_TEAM(MarginQueryRole::staffFragment),
    BID_PROJECTLEADER(MarginQueryRole::ownerFragment),
    BID_TEAMLEADER(MarginQueryRole::ownerFragment),
    UNKNOWN(MarginQueryRole::staffFragment);

    private final BiFunction<String, String, String> fragment;

    /** Case-insensitive lookup mapping role strings to policies.
     *  从 {@link RoleProfileCatalog} 派生，避免硬编码角色码。 */
    private static final Map<String, MarginQueryRole> LOOKUP = new HashMap<>();
    static {
        for (MarginQueryRole r : values()) {
            LOOKUP.put(r.name().toLowerCase(Locale.ROOT), r);
        }
        // 从 RoleProfileCatalog 派生：角色码（连字符形式）→ 策略
        registerAlias(RoleProfileCatalog.BID_SPECIALIST_CODE, BID_TEAM);
        registerAlias(RoleProfileCatalog.SALES_CODE, BID_PROJECTLEADER);
        registerAlias(RoleProfileCatalog.BID_LEAD_CODE, BID_TEAMLEADER);
        registerAlias(RoleProfileCatalog.BID_ADMIN_CODE, ADMIN);
        registerAlias(RoleProfileCatalog.ADMIN_STAFF_CODE, STAFF);
        registerAlias(RoleProfileCatalog.BID_OTHER_DEPT_CODE, STAFF);
    }

    /** 注册角色码别名：同时注册原始形式和 authority 形式（连字符→下划线）。 */
    private static void registerAlias(String roleCode, MarginQueryRole policy) {
        String lower = roleCode.toLowerCase(Locale.ROOT);
        LOOKUP.put(lower, policy);
        // authority 形式：bid-TeamLeader → bid_teamleader
        LOOKUP.put(lower.replace("-", "_"), policy);
    }

    MarginQueryRole(final BiFunction<String, String, String> fragment) {
        this.fragment = fragment;
    }

    /** Resolve a runtime role string to a typed policy, defaulting to UNKNOWN. */
    static MarginQueryRole from(final String role) {
        if (role == null) {
            return UNKNOWN;
        }
        return LOOKUP.getOrDefault(role.toLowerCase(Locale.ROOT), UNKNOWN);
    }

    /** SQL fragment ({@code AND (...)} or empty) for this role. */
    String apply(final String pa, final String pi) {
        return fragment.apply(pa, pi);
    }

    private static String staffFragment(final String pa, final String pi) {
        return " AND (" + pa + ".manager_id = :muid"
                + " OR EXISTS (SELECT 1 FROM project_team_members ptm"
                + " WHERE ptm.project_id = " + pa + ".id"
                + " AND ptm.member_id = :muid))";
    }

    private static String ownerFragment(final String pa, final String pi) {
        return " AND (" + pi + ".owner_user_id = :muid"
                + " OR " + pa + ".manager_id = :muid)";
    }
}
