package com.xiyu.bid.analytics.integration;

import com.xiyu.bid.support.NoOpPasswordEncryptionTestConfig;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.main.allow-bean-definition-overriding=true",
        "spring.jpa.properties.hibernate.generate_statistics=true"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ActiveProfiles("test")
@Import(NoOpPasswordEncryptionTestConfig.class)
class AuditOperationalAnalyticsIntegrationTest extends AbstractAuditOperationalAnalyticsIntegrationTest {

    @Test
    @WithMockUser(roles = {"ADMIN"})
    void auditEndpoint_ShouldReturnFilteredLogsAndSummary() throws Exception {
        mockMvc.perform(get("/api/audit")
                        .param("module", "project")
                        .param("action", "update")
                        .param("keyword", "status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items[0].actionType").value("update"))
                .andExpect(jsonPath("$.data.items[0].module").value("project"))
                .andExpect(jsonPath("$.data.items[0].target").value(project.getId().toString()))
                .andExpect(jsonPath("$.data.summary.failedCount").value(0))
                .andExpect(jsonPath("$.data.summary.totalCount").value(1));

        mockMvc.perform(get("/api/audit")
                        .param("status", "failed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].status").value("failed"))
                .andExpect(jsonPath("$.data.summary.failedCount").value(1));
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    void analyticsEndpoints_ShouldReturnRealProductLinesAndDrillDownData() throws Exception {
        mockMvc.perform(get("/api/analytics/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.summaryStats.totalTenders").value(3))
                .andExpect(jsonPath("$.data.summaryStats.activeProjects").value(1))
                .andExpect(jsonPath("$.data.summaryStats.pendingTasks").value(0))
                .andExpect(jsonPath("$.data.statusDistribution.BIDDING").value(1))
                .andExpect(jsonPath("$.data.topCompetitors[0].name").value("中国政府采购网"));

        resetStatistics();
        mockMvc.perform(get("/api/analytics/product-lines"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[*].name", hasItem("智慧办公")));
        assertQueryCountAtMost(8);

        resetStatistics();
        mockMvc.perform(get("/api/analytics/drilldown/revenue")
                        .param("status", "BIDDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.metricKey").value("revenue"))
                .andExpect(jsonPath("$.data.items[0].title").value("智慧办公平台采购"))
                .andExpect(jsonPath("$.data.summary.totalCount").value(1));
        assertQueryCountAtMost(2);

        resetStatistics();
        mockMvc.perform(get("/api/analytics/drill-down")
                        .param("type", "trend")
                        .param("key", currentMonthKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stats.totalParticipation").value(3))
                .andExpect(jsonPath("$.data.projects[0].name").value("智慧办公实施项目"));
        assertQueryCountAtMost(8);

        resetStatistics();
        mockMvc.perform(get("/api/analytics/drill-down")
                        .param("type", "competitor")
                        .param("key", "中国政府采购网"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stats.totalParticipation").value(3))
                .andExpect(jsonPath("$.data.files[0].name").value("智慧办公实施项目_export.json"));
        assertQueryCountAtMost(8);

        resetStatistics();
        mockMvc.perform(get("/api/analytics/drilldown/projects")
                        .param("status", "IN_PROGRESS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.metricKey").value("projects"))
                .andExpect(jsonPath("$.data.items[0].title").value("智慧办公实施项目"))
                .andExpect(jsonPath("$.data.summary.activeCount").value(1));
        assertQueryCountAtMost(2);

        resetStatistics();
        mockMvc.perform(get("/api/analytics/drilldown/team")
                        .param("role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.metricKey").value("team"))
                .andExpect(jsonPath("$.data.items[0].title").value("审计管理员"))
                .andExpect(jsonPath("$.data.items[0].count").value(1))
                .andExpect(jsonPath("$.data.items[0].managedProjectCount").value(1))
                .andExpect(jsonPath("$.data.summary.totalCompletedTasks").value(0));
        assertQueryCountAtMost(5);
    }

    @Test
    @WithMockUser(username = "analytics-staff", roles = {"STAFF"})
    void analyticsEndpoints_ForStaff_ShouldOnlyReturnAccessibleProjectData() throws Exception {
        User staffUser = userRepository.save(User.builder()
                .username("analytics-staff")
                .password("XiyuDemo!2026")
                .email("analytics-staff@example.com")
                .fullName("统计普通用户")
                .role(User.Role.STAFF)
                .roleProfile(roleProfileRepository.save(com.xiyu.bid.entity.RoleProfile.builder()
                        .code("staff")
                        .name("普通员工")
                        .dataScope("self")
                        .build()))
                .enabled(true)
                .build());
        Tender visibleTender = tenderRepository.save(Tender.builder()
                .title("普通用户可见标讯")
                .source("员工平台")
                .budget(new java.math.BigDecimal("200000"))
                .status(Tender.Status.TRACKING)
                .aiScore(70)
                .riskLevel(Tender.RiskLevel.MEDIUM)
                .build());
        projectRepository.save(Project.builder()
                .name("普通用户可见项目")
                .tenderId(visibleTender.getId())
                .status(Project.Status.BIDDING)
                .managerId(staffUser.getId())
                .teamMembers(List.of(staffUser.getId()))
                .startDate(java.time.LocalDateTime.now().minusDays(1))
                .endDate(java.time.LocalDateTime.now().plusDays(8))
                .build());

        mockMvc.perform(get("/api/analytics/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.summaryStats.totalTenders").value(1))
                .andExpect(jsonPath("$.data.summaryStats.activeProjects").value(1));

        mockMvc.perform(get("/api/analytics/product-lines"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[*].name", hasItem("综合解决方案")))
                .andExpect(jsonPath("$.data[*].name", not(hasItem("智慧办公平台采购"))));

        mockMvc.perform(get("/api/analytics/drilldown/projects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[*].title", hasItem("普通用户可见项目")))
                .andExpect(jsonPath("$.data.items[*].title", not(hasItem("智慧办公实施项目"))));
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    void analyticsEndpoints_ShouldReturnWinRateAndTeamDrillDownData() throws Exception {
        mockMvc.perform(get("/api/analytics/drilldown/win-rate")
                        .param("outcome", "WON"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.metricKey").value("win-rate"))
                .andExpect(jsonPath("$.data.items[0].title").value("Server升级采购"))
                .andExpect(jsonPath("$.data.summary.totalCount").value(1))
                .andExpect(jsonPath("$.data.summary.wonCount").value(1));

        mockMvc.perform(get("/api/analytics/drilldown/team")
                        .param("role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.metricKey").value("team"))
                .andExpect(jsonPath("$.data.items[0].title").value("审计管理员"))
                .andExpect(jsonPath("$.data.items[0].count").value(1))
                .andExpect(jsonPath("$.data.items[0].managedProjectCount").value(1))
                .andExpect(jsonPath("$.data.summary.totalCompletedTasks").value(0));
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    void customerTypeAnalytics_ShouldUseProjectCustomerTypeAndKeepEmptyValuesUncategorized() throws Exception {
        project.setCustomer("华东政务中心");
        project.setCustomerType("政府客户");
        project.setIndustry("智慧办公");
        projectRepository.save(project);

        Tender legacyTender = tenderRepository.save(Tender.builder()
                .title("能源平台历史项目")
                .source("能源行业平台")
                .budget(new java.math.BigDecimal("300000"))
                .status(Tender.Status.TRACKING)
                .aiScore(66)
                .riskLevel(Tender.RiskLevel.MEDIUM)
                .build());

        projectRepository.save(Project.builder()
                .name("历史客户类型空值项目")
                .tenderId(legacyTender.getId())
                .status(Project.Status.BIDDING)
                .managerId(adminUser.getId())
                .teamMembers(List.of(adminUser.getId()))
                .customer("西部能源集团")
                .industry("能源")
                .budget(new java.math.BigDecimal("300000"))
                .startDate(java.time.LocalDateTime.now().minusDays(1))
                .endDate(java.time.LocalDateTime.now().plusDays(8))
                .build());

        mockMvc.perform(get("/api/analytics/customer-types"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalProjectCount").value(2))
                .andExpect(jsonPath("$.data.uncategorizedProjectCount").value(1))
                .andExpect(jsonPath("$.data.dimensions[*].customerType", hasItems("政府客户", "未分类")))
                .andExpect(jsonPath("$.data.dimensions[*].customerType", not(hasItem("能源"))));

        mockMvc.perform(get("/api/analytics/drilldown/customer-type")
                        .param("customerType", "未分类"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].projectName").value("历史客户类型空值项目"))
                .andExpect(jsonPath("$.data[0].customerType").value("未分类"));
    }
}
