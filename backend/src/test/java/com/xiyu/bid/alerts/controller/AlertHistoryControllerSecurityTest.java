package com.xiyu.bid.alerts.controller;

import com.xiyu.bid.alerts.service.AlertHistoryCommandService;
import com.xiyu.bid.alerts.service.AlertHistoryQueryService;
import com.xiyu.bid.alerts.service.AlertHistoryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AlertHistoryController 权限收紧测试。
 * <p>权限规则：仅 ADMIN、投标管理员(/bidAdmin→ROLE_BIDADMIN)、投标组长(bid-TeamLeader→ROLE_BID_TEAMLEADER) 可访问；
 * 投标专员(bid-Team)、行政人员(bid-administration) 等普通用户不可读取或操作告警历史。</p>
 */
@SpringBootTest(properties = "spring.main.allow-bean-definition-overriding=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AlertHistoryControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AlertHistoryService alertHistoryService;

    @MockBean
    private AlertHistoryQueryService alertHistoryQueryService;

    @MockBean
    private AlertHistoryCommandService alertHistoryCommandService;

    @Test
    @WithMockUser(roles = {"BID_TEAMLEADER"})
    @DisplayName("投标组长应可访问告警历史端点")
    void bidTeamLeader_ShouldAccessAlertHistoryEndpoints() throws Exception {
        mockMvc.perform(get("/api/alerts/history")).andExpect(status().isOk());
        mockMvc.perform(get("/api/alerts/history/1")).andExpect(status().isOk());
        mockMvc.perform(get("/api/alerts/history/unresolved")).andExpect(status().isOk());
        mockMvc.perform(patch("/api/alerts/history/1/acknowledge")).andExpect(status().isOk());
        mockMvc.perform(get("/api/alerts/history/statistics")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = {"BIDADMIN"})
    @DisplayName("投标管理员应可访问告警历史端点")
    void bidAdmin_ShouldAccessAlertHistoryEndpoints() throws Exception {
        mockMvc.perform(get("/api/alerts/history")).andExpect(status().isOk());
        mockMvc.perform(get("/api/alerts/history/statistics")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    @DisplayName("管理员应可访问告警历史端点")
    void admin_ShouldAccessAlertHistoryEndpoints() throws Exception {
        mockMvc.perform(get("/api/alerts/history")).andExpect(status().isOk());
        mockMvc.perform(get("/api/alerts/history/statistics")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = {"BID_TEAM"})
    @DisplayName("投标专员不应访问告警历史端点（权限收紧）")
    void bidTeam_ShouldBeForbiddenAlertHistoryEndpoints() throws Exception {
        mockMvc.perform(get("/api/alerts/history")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/alerts/history/1")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/alerts/history/unresolved")).andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/alerts/history/1/acknowledge")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/alerts/history/statistics")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = {"BID_ADMINISTRATION"})
    @DisplayName("行政人员不应访问告警历史端点（权限收紧）")
    void bidAdministration_ShouldBeForbiddenAlertHistoryEndpoints() throws Exception {
        mockMvc.perform(get("/api/alerts/history")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/alerts/history/statistics")).andExpect(status().isForbidden());
    }

    @Test
    void allEndpoints_ShouldRequireAuthorizedRole() throws Exception {
        // 验证类级 @PreAuthorize 已收紧到 hasAnyRole('ADMIN', 'BIDADMIN', 'BID_TEAMLEADER')
        PreAuthorize classAnnotation = AlertHistoryController.class.getAnnotation(PreAuthorize.class);
        assertThat(classAnnotation).isNotNull();
        String expression = classAnnotation.value();
        assertThat(expression).contains("hasAnyRole");
        assertThat(expression).contains("ADMIN");
        assertThat(expression).contains("BIDADMIN");
        assertThat(expression).contains("BID_TEAMLEADER");
    }
}
