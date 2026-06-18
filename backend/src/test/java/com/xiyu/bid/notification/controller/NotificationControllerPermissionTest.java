package com.xiyu.bid.notification.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CO-261 根因一：NotificationController 类级 @PreAuthorize("hasRole('ADMIN')")
 * 把所有读取端点锁死，sales（authority = ROLE_MANAGER）访问通知面板/铃铛角标全部 403。
 * <p>
 * 修复：类级注解从 hasRole('ADMIN') 放宽为 isAuthenticated()（所有已认证用户可读，
 * 后端按 currentUser.getId() 过滤），POST /api/admin/notifications 保留方法级 ADMIN 守卫。
 * <p>
 * 注：不能直接删除类级注解——ArchitectureTest RULE 15 强制每个 @RestController 必须有
 * 类级 @PreAuthorize。改用 isAuthenticated() 既满足 RULE 15，又放开读取端点。
 */
@DisplayName("NotificationController @PreAuthorize 注解契约（CO-261 根因一）")
class NotificationControllerPermissionTest {

    @Test
    @DisplayName("类级 @PreAuthorize 放宽为 isAuthenticated()，允许已认证用户读取自己的通知")
    void classLevelPreAuthorize_allowsAuthenticatedUsers() {
        PreAuthorize annotation = NotificationController.class
            .getAnnotation(PreAuthorize.class);

        assertThat(annotation)
            .as("NotificationController 必须保留类级 @PreAuthorize（ArchitectureTest RULE 15）")
            .isNotNull();
        assertThat(annotation.value())
            .as("类级注解应从 hasRole('ADMIN') 放宽为 isAuthenticated()，"
                + "让 sales 等非 ADMIN 角色也能读取自己的通知")
            .isEqualTo("isAuthenticated()");
    }

    @Test
    @DisplayName("POST /api/admin/notifications 仍保留方法级 ADMIN 守卫")
    void createNotification_keepsMethodLevelAdminGuard() throws NoSuchMethodException {
        PreAuthorize annotation = NotificationController.class
            .getMethod("createNotification",
                com.xiyu.bid.notification.dto.CreateNotificationRequest.class,
                org.springframework.security.core.userdetails.UserDetails.class)
            .getAnnotation(PreAuthorize.class);

        assertThat(annotation)
            .as("createNotification 必须保留方法级 @PreAuthorize")
            .isNotNull();
        assertThat(annotation.value())
            .as("管理员推送通知入口仍限 ADMIN")
            .isEqualTo("hasRole('ADMIN')");
    }
}
