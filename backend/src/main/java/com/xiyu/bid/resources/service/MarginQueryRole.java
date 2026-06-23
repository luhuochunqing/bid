package com.xiyu.bid.resources.service;

import java.util.HashMap;
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
 * <p>Role code aliases are aligned with the OSS doc role profile codes and
 * authority names (e.g. {@code bid-Team}/{@code BID_TEAM} for 投标专员,
 * {@code bid-projectLeader}/{@code BID_PROJECTLEADER} for 投标项目负责人).</p>
 */
enum MarginQueryRole {

    ADMIN((pa, pi) -> ""),
    MANAGER((pa, pi) -> ""),
    STAFF(MarginQueryRole::staffFragment),
    BID_TEAM(MarginQueryRole::staffFragment),
    SALES(MarginQueryRole::ownerFragment),
    BID_TEAMLEADER(MarginQueryRole::ownerFragment),
    UNKNOWN(MarginQueryRole::staffFragment);

    private final BiFunction<String, String, String> fragment;

    /** Case-insensitive lookup mapping role strings to policies. */
    private static final Map<String, MarginQueryRole> LOOKUP = new HashMap<>();
    static {
        for (MarginQueryRole r : values()) {
            LOOKUP.put(r.name().toLowerCase(), r);
        }
        // Role profile code aliases (aligned with OSS doc)
        LOOKUP.put("bid-team", BID_TEAM);
        LOOKUP.put("bid-projectleader", SALES);
        LOOKUP.put("bid-teamleader", BID_TEAMLEADER);
        // Authority name aliases (underscore form, aligned with OSS doc)
        LOOKUP.put("bid_team", BID_TEAM);
        LOOKUP.put("bid_projectleader", SALES);
        LOOKUP.put("bid_teamleader", BID_TEAMLEADER);
    }

    MarginQueryRole(final BiFunction<String, String, String> fragment) {
        this.fragment = fragment;
    }

    /** Resolve a runtime role string to a typed policy, defaulting to UNKNOWN. */
    static MarginQueryRole from(final String role) {
        if (role == null) {
            return UNKNOWN;
        }
        return LOOKUP.getOrDefault(role.toLowerCase(), UNKNOWN);
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
