package com.xiyu.bid.user.core;

import com.xiyu.bid.entity.RoleProfile;
import com.xiyu.bid.entity.User;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DbRoleSnapshotResolver 独立单元测试。
 */
class DbRoleSnapshotResolverTest {

    @Test
    void resolveRoleCode_returns_roleProfileCode() {
        RoleProfile roleProfile = RoleProfile.builder()
                .code("bid-projectLeader")
                .name("投标项目负责人")
                .build();
        User user = User.builder()
                .username("zhou")
                .fullName("周子靖")
                .roleProfile(roleProfile)
                .build();

        assertThat(DbRoleSnapshotResolver.resolveRoleCode(user)).isEqualTo("bid-projectLeader");
    }

    @Test
    void resolveRoleCode_nullUser_returnsNull() {
        assertThat(DbRoleSnapshotResolver.resolveRoleCode(null)).isNull();
    }

    @Test
    void resolveRoleCode_userWithoutRoleProfile_returnsFallback() {
        User user = User.builder()
                .username("legacy")
                .fullName("历史用户")
                .role(User.Role.MANAGER)
                .build();

        assertThat(DbRoleSnapshotResolver.resolveRoleCode(user)).isEqualTo("manager");
    }
}
