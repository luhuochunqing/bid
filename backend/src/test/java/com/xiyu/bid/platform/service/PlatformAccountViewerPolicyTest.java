package com.xiyu.bid.platform.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.xiyu.bid.entity.User;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link PlatformAccountViewerPolicy} 单元测试。
 *
 * <p>重点覆盖 {@code canExportAccount} 的白名单授权矩阵。
 */
@DisplayName("PlatformAccountViewerPolicy 权限策略")
class PlatformAccountViewerPolicyTest {

    @Nested
    @DisplayName("canExportAccount — 导出白名单授权")
    class CanExportAccount {

        @Test
        @DisplayName("特权角色（admin）放行")
        void adminRole_allowed() {
            User user = User.builder().username("admin01").build();
            assertThat(PlatformAccountViewerPolicy.canExportAccount("admin", user, List.of())).isTrue();
        }

        @Test
        @DisplayName("特权角色（bid-teamleader）放行")
        void teamLeaderRole_allowed() {
            User user = User.builder().username("leader01").build();
            assertThat(PlatformAccountViewerPolicy.canExportAccount("bid-teamleader", user, List.of())).isTrue();
        }

        @Test
        @DisplayName("特权角色（/bidadmin）放行")
        void bidAdminRole_allowed() {
            User user = User.builder().username("bidadmin01").build();
            assertThat(PlatformAccountViewerPolicy.canExportAccount("/bidadmin", user, List.of())).isTrue();
        }

        @Test
        @DisplayName("白名单用户（00444）放行，即使角色非特权")
        void whitelistUser_allowed() {
            User user = User.builder().username("00444").build();
            assertThat(PlatformAccountViewerPolicy.canExportAccount("bid-Team", user, List.of("00444"))).isTrue();
        }

        @Test
        @DisplayName("非白名单的投标专员拒绝")
        void nonWhitelistBidTeam_denied() {
            User user = User.builder().username("00999").build();
            assertThat(PlatformAccountViewerPolicy.canExportAccount("bid-Team", user, List.of("00444"))).isFalse();
        }

        @Test
        @DisplayName("白名单为空时，非特权用户拒绝")
        void emptyWhitelist_nonPrivileged_denied() {
            User user = User.builder().username("00444").build();
            assertThat(PlatformAccountViewerPolicy.canExportAccount("bid-Team", user, List.of())).isFalse();
        }

        @Test
        @DisplayName("null 用户拒绝")
        void nullUser_denied() {
            assertThat(PlatformAccountViewerPolicy.canExportAccount("admin", null, List.of("00444"))).isFalse();
        }

        @Test
        @DisplayName("null 白名单视为空列表，非特权用户拒绝")
        void nullWhitelist_nonPrivileged_denied() {
            User user = User.builder().username("00444").build();
            assertThat(PlatformAccountViewerPolicy.canExportAccount("bid-Team", user, null)).isFalse();
        }

        @Test
        @DisplayName("checkCanExportAccount — 非特权非白名单用户抛 AccessDeniedException")
        void checkCanExportAccount_deniedUser_throwsException() {
            User user = User.builder().username("00999").build();
            assertThatThrownBy(() ->
                    PlatformAccountViewerPolicy.checkCanExportAccount("bid-Team", user, List.of("00444")))
                    .isInstanceOf(org.springframework.security.access.AccessDeniedException.class)
                    .hasMessageContaining("白名单");
        }
    }
}
