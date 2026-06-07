package com.xiyu.bid.analytics.controller;

import com.xiyu.bid.analytics.dto.SummaryStats;
import com.xiyu.bid.analytics.service.DashboardAnalyticsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.main.allow-bean-definition-overriding=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DashboardSummaryContractTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DashboardAnalyticsService dashboardAnalyticsService;

    @Test
    @WithMockUser(username = "alice", roles = {"STAFF"})
    void getSummaryStats_returnsExpectedShape() throws Exception {
        SummaryStats stats = SummaryStats.builder()
                .totalTenders(42L)
                .activeProjects(7L)
                .pendingTasks(15L)
                .totalBudget(new BigDecimal("9800000.00"))
                .successRate(68.5)
                .build();

        when(dashboardAnalyticsService.getSummaryStats()).thenReturn(stats);

        mockMvc.perform(get("/api/analytics/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalTenders").value(42))
                .andExpect(jsonPath("$.data.activeProjects").value(7))
                .andExpect(jsonPath("$.data.pendingTasks").value(15))
                .andExpect(jsonPath("$.data.totalBudget").value(9800000.00))
                .andExpect(jsonPath("$.data.successRate").value(68.5));
    }
}
