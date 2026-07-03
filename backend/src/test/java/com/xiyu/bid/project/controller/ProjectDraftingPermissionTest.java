package com.xiyu.bid.project.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 2.3.2 最终标书审核与投标提交 @PreAuthorize 注解契约测试（飞书《投标项目·权限矩阵》2.3.2）。
 *
 * <p>锁定标书提交/审核端点的角色准入。审核（approve/reject）权限在 Service 层
 * BidReviewPolicy 按审核人身份实例级校验（临时选定），Controller 层不区分角色。
 */
class ProjectDraftingPermissionTest {

    @Test
    @DisplayName("2.3.2 分配投标团队 assignLeads：仅管理员/组长（文档：分配投标团队）")
    void assignLeads_preAuthorize_adminBidLeadBidAdminOnly() {
        String value = findMethod("assignLeads").getAnnotation(PreAuthorize.class).value();
        assertThat(value).isEqualTo("hasAnyRole('ADMIN', 'BID_TEAMLEADER', 'BIDADMIN')");
    }

    @Test
    @DisplayName("2.3.2 最终投标提交 submitBid：管理员/组长/投标负责人/辅助/项目负责人（文档：提交投标）")
    void submitBid_preAuthorize_allowsBidTeamAndProjectLeader() {
        String value = findMethod("submitBid").getAnnotation(PreAuthorize.class).value();
        assertThat(value).contains("'BID_TEAM'", "'BID_PROJECTLEADER'", "'BID_TEAMLEADER'");
    }

    @Test
    @DisplayName("2.3.2 标书审核 approve：无方法级 @PreAuthorize（鉴权下沉 BidReviewPolicy，按审核人身份实例级校验）")
    void approve_noMethodLevelPreAuthorize_authzInServiceLayer() {
        // CO-315：approve/reject 的鉴权下沉到 Service 层 BidReviewPolicy（临时选定的审核人）
        // Controller 层不区分角色，依赖类级 isAuthenticated + Service 实例级校验
        Method method = findMethod("approve");
        // approve 方法无方法级 @PreAuthorize 注解（鉴权在 Service 层）
        assertThat(method.getAnnotation(PreAuthorize.class)).isNull();
    }

    @Test
    @DisplayName("2.3.2 标书审核 reject：无方法级 @PreAuthorize（同 approve，鉴权在 Service 层）")
    void reject_noMethodLevelPreAuthorize_authzInServiceLayer() {
        Method method = findMethod("reject");
        assertThat(method.getAnnotation(PreAuthorize.class)).isNull();
    }

    private Method findMethod(String name) {
        for (Method m : ProjectDraftingController.class.getDeclaredMethods()) {
            if (m.getName().equals(name)) {
                return m;
            }
        }
        throw new AssertionError("方法 " + name + " 未找到");
    }
}
