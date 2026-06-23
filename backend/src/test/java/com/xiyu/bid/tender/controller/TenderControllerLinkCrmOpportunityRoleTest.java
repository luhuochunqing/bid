package com.xiyu.bid.tender.controller;

import com.xiyu.bid.tender.dto.TenderCrmLinkRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.userdetails.UserDetails;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CO-310: 验证 PATCH /api/tenders/{id}/crm-opportunity 端点的 @PreAuthorize 注解
 * 包含 BID_PROJECTLEADER 等业务角色,确保 bid-projectLeader 角色能关联 CRM 商机(不再 403)。
 *
 * <p>采用反射方式校验注解值,与 {@link TenderControllerPermissionTest} 保持一致。
 * 服务层已有 {@code commandAccessGuard.assertCanUpdateTender} 实例级守卫,
 * 注解层放行业务角色不会让任意 sales 改任意标讯。
 */
class TenderControllerLinkCrmOpportunityRoleTest {

    @Test
    @DisplayName("linkCrmOpportunity 注解包含 BID_PROJECTLEADER 角色(R2 修复核心)")
    void linkCrmOpportunity_allowsSalesRole() throws NoSuchMethodException {
        PreAuthorize annotation = getLinkCrmOpportunityAnnotation();

        // 注解值必须包含 BID_PROJECTLEADER,否则 bid-projectLeader 角色会被 403 拦截
        assertThat(annotation.value()).contains("BID_PROJECTLEADER");
    }

    @Test
    @DisplayName("linkCrmOpportunity 注解包含 BID_TEAMLEADER/BIDADMIN/BID_TEAM 业务角色")
    void linkCrmOpportunity_allowsBusinessRoles() throws NoSuchMethodException {
        PreAuthorize annotation = getLinkCrmOpportunityAnnotation();

        // 与 createTender 端点(TenderControllerPermissionTest)对齐,覆盖核心业务角色
        assertThat(annotation.value()).contains("BID_TEAMLEADER");
        assertThat(annotation.value()).contains("BIDADMIN");
        assertThat(annotation.value()).contains("BID_TEAM");
    }

    @Test
    @DisplayName("linkCrmOpportunity 注解包含 ADMIN 角色(不回归)")
    void linkCrmOpportunity_allowsAdminRole() throws NoSuchMethodException {
        PreAuthorize annotation = getLinkCrmOpportunityAnnotation();

        assertThat(annotation.value()).contains("ADMIN");
    }

    @Test
    @DisplayName("linkCrmOpportunity 注解放宽后仍保留角色白名单(非 isAuthenticated)")
    void linkCrmOpportunity_stillUsesRoleWhitelist() throws NoSuchMethodException {
        PreAuthorize annotation = getLinkCrmOpportunityAnnotation();

        // 不能改成 isAuthenticated(),保留显式角色白名单更稳健
        assertThat(annotation.value()).startsWith("hasAnyRole(");
    }

    private PreAuthorize getLinkCrmOpportunityAnnotation() throws NoSuchMethodException {
        Method method = TenderController.class.getMethod(
                "linkCrmOpportunity",
                Long.class,
                TenderCrmLinkRequest.class,
                UserDetails.class
        );
        return method.getAnnotation(PreAuthorize.class);
    }
}
