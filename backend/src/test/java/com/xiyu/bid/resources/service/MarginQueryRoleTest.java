package com.xiyu.bid.resources.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link MarginQueryRole}.
 *
 * <p>Main goal: verify that arbitrary role strings are normalized to a known
 * policy, so untrusted input cannot influence SQL construction.</p>
 */
class MarginQueryRoleTest {

    private static final String PA = "p";
    private static final String PI = "pid";

    @Test
    void adminAndManagerHaveNoFragment() {
        assertThat(MarginQueryRole.from("admin").apply(PA, PI)).isEmpty();
        assertThat(MarginQueryRole.from("MANAGER").apply(PA, PI)).isEmpty();
    }

    @Test
    void staffLikeRolesGetTeamMemberFragment() {
        String fragment = MarginQueryRole.from("staff").apply(PA, PI);
        assertThat(fragment).contains("p.manager_id = :muid");
        assertThat(fragment).contains("project_team_members");
        assertThat(fragment).contains("ptm.member_id = :muid");
        assertThat(fragment).doesNotContain("owner_user_id");

        assertThat(MarginQueryRole.from("bid_specialist").apply(PA, PI))
                .isEqualTo(fragment);
        assertThat(MarginQueryRole.from("TASK_EXECUTOR").apply(PA, PI))
                .isEqualTo(fragment);
    }

    @Test
    void salesLikeRolesGetOwnerFragment() {
        String fragment = MarginQueryRole.from("sales").apply(PA, PI);
        assertThat(fragment).contains("pid.owner_user_id = :muid");
        assertThat(fragment).contains("p.manager_id = :muid");
        assertThat(fragment).doesNotContain("project_team_members");

        assertThat(MarginQueryRole.from("BID_LEAD").apply(PA, PI))
                .isEqualTo(fragment);
    }

    @Test
    void unknownOrNullRolesFallbackToMostRestrictiveFragment() {
        String fallback = MarginQueryRole.from("staff").apply(PA, PI);
        assertThat(MarginQueryRole.from("attacker' OR '1'='1").apply(PA, PI))
                .isEqualTo(fallback);
        assertThat(MarginQueryRole.from("").apply(PA, PI))
                .isEqualTo(fallback);
    }

    @Test
    void appendRoleRejectsUnknownInputWithoutSqlInjection() {
        StringBuilder sql = new StringBuilder("SELECT 1 FROM fees f");
        String maliciousRole = "admin'; DROP TABLE fees; --";
        MarginQuerySupport.appendRole(sql, 1L, maliciousRole, "f", "pid");

        String built = sql.toString();
        assertThat(built).doesNotContain("DROP TABLE");
        assertThat(built).contains("f.manager_id = :muid");
        assertThat(built).contains("project_team_members");
    }
}
