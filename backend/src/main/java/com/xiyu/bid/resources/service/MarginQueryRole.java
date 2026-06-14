package com.xiyu.bid.resources.service;

import java.util.Arrays;
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
 */
enum MarginQueryRole {

    ADMIN((pa, pi) -> ""),
    MANAGER((pa, pi) -> ""),
    STAFF(MarginQueryRole::staffFragment),
    BID_SPECIALIST(MarginQueryRole::staffFragment),
    TASK_EXECUTOR(MarginQueryRole::staffFragment),
    SALES(MarginQueryRole::ownerFragment),
    BID_LEAD(MarginQueryRole::ownerFragment),
    UNKNOWN(MarginQueryRole::staffFragment);

    private final BiFunction<String, String, String> fragment;

    MarginQueryRole(final BiFunction<String, String, String> fragment) {
        this.fragment = fragment;
    }

    /** Resolve a runtime role string to a typed policy, defaulting to UNKNOWN. */
    static MarginQueryRole from(final String role) {
        return Arrays.stream(values())
                .filter(r -> r.name().equalsIgnoreCase(role))
                .findFirst()
                .orElse(UNKNOWN);
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
