package com.xiyu.bid.entity;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 RoleProfile.splitStrings 的防御性去引号逻辑。
 *
 * <p>根因：V1118/V1121/V1122/V1123 Flyway 迁移使用
 * {@code CONCAT(menu_permissions, ',"tender.view"')} 写入了字面双引号，
 * 导致权限字符串存储为 {@code "tender.view"}（带引号）。
 *
 * <p>修复：{@code splitStrings} 解析时去掉首尾字面双引号，
 * 兼容历史脏数据；新迁移 V1174 同步清理 DB 存量数据。
 */
class RoleProfileMenuPermissionsQuotedTest {

    @Test
    void shouldStripSurroundingDoubleQuotesFromPermissions() {
        // 模拟 V1118/V1121/V1122/V1123 写入的脏数据
        RoleProfile profile = new RoleProfile();
        profile.setMenuPermissionsValue("dashboard,\"tender.view\",personnel.view");

        List<String> permissions = profile.getMenuPermissions();

        assertThat(permissions).containsExactly("dashboard", "tender.view", "personnel.view");
    }

    @Test
    void shouldStripQuotesFromAllQuotedPermissions() {
        // 模拟实际的 /bidAdmin 脏数据：5 个权限带引号
        RoleProfile profile = new RoleProfile();
        profile.setMenuPermissionsValue(
                "dashboard,knowledge,resource,warehouse.manage,"
                        + "\"tender.view\",\"personnel.view\",\"personnel.manage\","
                        + "\"performance.manage\",\"qualification.manage\"");

        List<String> permissions = profile.getMenuPermissions();

        assertThat(permissions).contains(
                "tender.view", "personnel.view", "personnel.manage",
                "performance.manage", "qualification.manage");
        // 不应包含任何带引号的字符串
        assertThat(permissions).noneMatch(p -> p.contains("\""));
    }

    @Test
    void shouldNotModifyUnquotedPermissions() {
        RoleProfile profile = new RoleProfile();
        profile.setMenuPermissionsValue("dashboard,bidding.manage,tender.view");

        List<String> permissions = profile.getMenuPermissions();

        assertThat(permissions).containsExactly("dashboard", "bidding.manage", "tender.view");
    }

    @Test
    void shouldHandleEmptyAndNullValues() {
        RoleProfile profile = new RoleProfile();
        profile.setMenuPermissionsValue(null);
        assertThat(profile.getMenuPermissions()).isEmpty();

        profile.setMenuPermissionsValue("");
        assertThat(profile.getMenuPermissions()).isEmpty();

        profile.setMenuPermissionsValue("   ");
        assertThat(profile.getMenuPermissions()).isEmpty();
    }

    @Test
    void shouldHandleSingleQuotedPermission() {
        RoleProfile profile = new RoleProfile();
        profile.setMenuPermissionsValue("\"tender.view\"");

        List<String> permissions = profile.getMenuPermissions();

        assertThat(permissions).containsExactly("tender.view");
    }

    @Test
    void shouldNotStripInteriorQuotes() {
        // 权限名中间不应有引号；如果有，仅去掉首尾
        RoleProfile profile = new RoleProfile();
        profile.setMenuPermissionsValue("\"a\"b\"");

        List<String> permissions = profile.getMenuPermissions();

        // 首尾去掉后是 a"b
        assertThat(permissions).containsExactly("a\"b");
    }
}
