package com.xiyu.bid.alerts.controller;

import com.xiyu.bid.alerts.entity.AlertHistory;
import com.xiyu.bid.alerts.service.AlertHistoryCommandService;
import com.xiyu.bid.alerts.service.AlertHistoryQueryService;
import com.xiyu.bid.alerts.service.AlertHistoryService;
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
    @WithMockUser(roles = {"STAFF"})
    void staff_ShouldNotAccessAlertHistoryReadAndAcknowledgeEndpoints() throws Exception {
        mockMvc.perform(get("/api/alerts/history")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/alerts/history/1")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/alerts/history/unresolved")).andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/alerts/history/1/acknowledge")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/alerts/history/statistics")).andExpect(status().isForbidden());
    }

    @Test
    void adminAndManager_ShouldRemainAllowedByMethodSecurityExpressions() throws Exception {
        assertHistoryExpression("getAllAlertHistories", int.class, int.class, String.class, String.class,
                String.class, AlertHistory.AlertLevel.class, Long.class, String.class);
        assertHistoryExpression("getAlertHistoryById", Long.class);
        assertHistoryExpression("getUnresolvedAlertHistories", int.class, int.class);
        assertHistoryExpression("acknowledgeAlertHistory", Long.class);
        assertHistoryExpression("getAlertStatistics");
    }

    private void assertHistoryExpression(String methodName, Class<?>... parameterTypes) throws Exception {
        PreAuthorize preAuthorize = AlertHistoryController.class
                .getMethod(methodName, parameterTypes)
                .getAnnotation(PreAuthorize.class);

        assertThat(preAuthorize).isNotNull();
        assertThat(preAuthorize.value()).contains("ADMIN", "MANAGER");
        assertThat(preAuthorize.value()).doesNotContain("STAFF");
    }
}
