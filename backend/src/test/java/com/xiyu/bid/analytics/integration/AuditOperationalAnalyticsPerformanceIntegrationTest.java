package com.xiyu.bid.analytics.integration;

import com.xiyu.bid.support.NoOpPasswordEncryptionTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.main.allow-bean-definition-overriding=true",
        "spring.jpa.properties.hibernate.generate_statistics=true"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ActiveProfiles("test")
@Import(NoOpPasswordEncryptionTestConfig.class)
class AuditOperationalAnalyticsPerformanceIntegrationTest extends AbstractAuditOperationalAnalyticsIntegrationTest {

    @Test
    @WithMockUser(roles = {"ADMIN"})
    void analyticsQueries_ShouldStayBelowNaiveBaselines() throws Exception {
        long productLineStatements = measureStatements(() -> mockMvc.perform(get("/api/analytics/product-lines"))
                .andExpect(status().isOk()));
        long productLineBaseline = measureStatements(this::runNaiveProductLineBaseline);
        assertThat(productLineStatements)
                .as("product-lines should use fewer SQL statements than the naive baseline")
                .isGreaterThan(0L)
                .isLessThan(productLineBaseline)
                .isLessThanOrEqualTo(4L);

        long drillDownStatements = measureStatements(() -> mockMvc.perform(get("/api/analytics/drill-down")
                .param("type", "trend")
                .param("key", currentMonthKey))
                .andExpect(status().isOk()));
        long drillDownBaseline = measureStatements(() -> runNaiveDrillDownBaseline("trend", currentMonthKey));
        assertThat(drillDownStatements)
                .as("drill-down should use fewer SQL statements than the naive baseline")
                .isGreaterThan(0L)
                .isLessThan(drillDownBaseline)
                .isLessThanOrEqualTo(8L);

        long winRateStatements = measureStatements(() -> mockMvc.perform(get("/api/analytics/drilldown/win-rate")
                .param("outcome", "WON"))
                .andExpect(status().isOk()));
        long winRateBaseline = measureStatements(this::runNaiveWinRateBaseline);
        assertThat(winRateStatements)
                .as("win-rate drill-down should use fewer SQL statements than the naive baseline")
                .isGreaterThan(0L)
                .isLessThan(winRateBaseline)
                .isLessThanOrEqualTo(8L);

        long teamStatements = measureStatements(() -> mockMvc.perform(get("/api/analytics/drilldown/team")
                .param("role", "ADMIN"))
                .andExpect(status().isOk()));
        long teamBaseline = measureStatements(this::runNaiveTeamBaseline);
        assertThat(teamStatements)
                .as("team drill-down should use fewer SQL statements than the naive baseline")
                .isGreaterThan(0L)
                .isLessThan(teamBaseline)
                .isLessThanOrEqualTo(8L);
    }
}
