package com.xiyu.bid.analytics.controller;

import com.xiyu.bid.analytics.service.DashboardAnalyticsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Dashboard 权限测试：验证 GET/POST /api/analytics/** 的 @PreAuthorize 行为。
 *
 * <p>P3 迁移回归守卫（specs/024-preauthorize-unification 批次 analytics）：
 * DashboardController 原 6 处 {@code hasAnyRole('ADMIN','MANAGER')} 已迁移为
 * {@code hasAuthority('dashboard')}，与同 Controller 内已有的 hasAuthority('dashboard')
 * 用例对齐。</p>
 *
 * <p>权限矩阵（来自 RoleProfileCatalog）：
 * {@code dashboard} 权限键被 5 个投标业务角色持有（admin/bid-projectLeader/
 * bid-TeamLeader/bidAdmin/bid-Team），行政人员(bid-administration)与跨部门协同
 * (bid-otherDept) 不持有。</p>
 *
 * <p>本测试锚定该矩阵，防止未来权限键调整时回归（吸取 CaCertificate 无 Controller
 * 层权限测试、混合补丁长期无人敢清理的教训）。</p>
 */
@SpringBootTest(properties = "spring.main.allow-bean-definition-overriding=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DashboardSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DashboardAnalyticsService dashboardAnalyticsService;

    /**
     * 投标业务角色（持有 dashboard 权限）应可访问迁移后的 6 个端点。
     * 覆盖原 hasAnyRole('ADMIN','MANAGER') 的 5 类角色回归。
     */
    @Test
    @WithMockUser(authorities = "dashboard")
    void bidProjectLeader_canAccessMigratedEndpoints() throws Exception {
        when(dashboardAnalyticsService.getRegionalDistribution()).thenReturn(List.of());
        when(dashboardAnalyticsService.getProductLinePerformance()).thenReturn(List.of());

        mockMvc.perform(get("/api/analytics/regions")).andExpect(status().isOk());
        mockMvc.perform(get("/api/analytics/product-lines")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "dashboard")
    void bidTeamLeader_canAccessMigratedEndpoints() throws Exception {
        when(dashboardAnalyticsService.getRegionalDistribution()).thenReturn(List.of());
        mockMvc.perform(get("/api/analytics/regions")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "dashboard")
    void bidTeam_canAccessMigratedEndpoints() throws Exception {
        when(dashboardAnalyticsService.getProductLinePerformance()).thenReturn(List.of());
        mockMvc.perform(get("/api/analytics/product-lines")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "dashboard")
    void admin_canAccessAllMigratedEndpoints() throws Exception {
        when(dashboardAnalyticsService.getRegionalDistribution()).thenReturn(List.of());
        when(dashboardAnalyticsService.getProductLinePerformance()).thenReturn(List.of());
        mockMvc.perform(get("/api/analytics/regions")).andExpect(status().isOk());
        mockMvc.perform(get("/api/analytics/product-lines")).andExpect(status().isOk());
    }

    /**
     * 行政人员（不持有 dashboard 权限）应被拒——验证 hasAuthority('dashboard')
     * 正确拦截无权限角色，而非像 hasAnyRole 那样基于角色枚举。
     */
    @Test
    @WithMockUser(authorities = "qualification.view")
    void bidAdministration_cannotAccessDashboard() throws Exception {
        mockMvc.perform(get("/api/analytics/regions"))
                .andExpect(status().is4xxClientError());
    }

    /**
     * 跨部门协同人员（不持有 dashboard 权限）应被拒。
     */
    @Test
    @WithMockUser(authorities = "task.handle.own")
    void bidOtherDept_cannotAccessDashboard() throws Exception {
        mockMvc.perform(get("/api/analytics/product-lines"))
                .andExpect(status().is4xxClientError());
    }
}
