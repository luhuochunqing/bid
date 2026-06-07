package com.xiyu.bid.bidresult.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.xiyu.bid.bidresult.entity.BidResultFetchResult;
import com.xiyu.bid.entity.Project;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import com.xiyu.bid.support.NoOpPasswordEncryptionTestConfig;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.main.allow-bean-definition-overriding=true")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ActiveProfiles("test")
@Import(NoOpPasswordEncryptionTestConfig.class)
class BidResultQueryAggregateIntegrationTest extends AbstractBidResultIntegrationTest {

    @BeforeEach
    void setUpProjects() {
        fixtures.createProject("聚合闭环项目-A", 85001L);
        fixtures.createProject("聚合闭环项目-B", 85002L);
        fixtures.createProject("聚合闭环项目-C", 85003L);
    }

    @Test
    @WithMockUser(username = "bid-admin", roles = {"ADMIN"})
    void overviewEndpoint_ShouldTrackPendingFetchReminderAndCompetitorCounts() throws Exception {
        assertOverview(0, 0, 0);

        mockMvc.perform(post("/api/bid-results/fetch"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.affectedCount").value(3));
        assertOverview(3, 0, 0);

        BidResultFetchResult lostFetch = latestFetchResult(BidResultFetchResult.Result.LOST);
        mockMvc.perform(post("/api/bid-results/fetch-results/{id}/confirm-with-data", lostFetch.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "remark": "聚合页确认补录",
                                  "skuCount": 11
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"));
        assertOverview(2, 1, 0);

        mockMvc.perform(post("/api/bid-results/competitor-wins")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectId": %d,
                                  "competitorName": "聚合竞对A",
                                  "skuCount": 8,
                                  "category": "网络设备",
                                  "discount": "91折",
                                  "paymentTerms": "月结45天",
                                  "wonAt": "2026-04-18",
                                  "amount": 560000,
                                  "notes": "overview 计数校验"
                                }
                                """.formatted(lostFetch.getProjectId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.competitorName").value("聚合竞对A"));

        httpSupport.createBidResultDocument(
                lostFetch.getProjectId(),
                lostFetch.getId(),
                "BID_RESULT_ANALYSIS",
                "overview-report.pdf"
        );
        assertOverview(2, 0, 1);
    }

    @Test
    @WithMockUser(username = "bid-admin", roles = {"ADMIN"})
    void fetchResultsEndpoint_ShouldExposeRepeatedFetchAndSyncRecordsInCurrentOrder() throws Exception {
        long oldIgnoredId = createOldIgnoredResult();

        mockMvc.perform(post("/api/bid-results/fetch"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.affectedCount").value(3));
        mockMvc.perform(post("/api/bid-results/fetch"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.affectedCount").value(3));

        BidResultFetchResult ignoredFetch = fetchResultRepository.findAllByOrderByFetchTimeDesc().stream()
                .filter(result -> result.getRegistrationType() == BidResultFetchResult.RegistrationType.FETCH)
                .findFirst()
                .orElseThrow();
        mockMvc.perform(post("/api/bid-results/fetch-results/{id}/ignore", ignoredFetch.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "comment": "重复同步批次"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(post("/api/bid-results/sync"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.affectedCount").value(3));

        String response = mockMvc.perform(get("/api/bid-results/fetch-results"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(10))
                .andExpect(jsonPath("$.data[9].id").value(oldIgnoredId))
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        JsonNode items = objectMapper.readTree(response).path("data");
        int pendingCount = 0;
        int confirmedCount = 0;
        int ignoredCount = 0;
        int fetchTypeCount = 0;
        int syncTypeCount = 0;
        int manualTypeCount = 0;
        JsonNode ignoredFetchNode = null;

        for (JsonNode item : items) {
            switch (item.path("status").asText()) {
                case "PENDING" -> pendingCount++;
                case "CONFIRMED" -> confirmedCount++;
                case "IGNORED" -> ignoredCount++;
                default -> throw new IllegalStateException("Unexpected status: " + item.path("status").asText());
            }
            switch (item.path("registrationType").asText()) {
                case "FETCH" -> fetchTypeCount++;
                case "SYNC" -> syncTypeCount++;
                case "MANUAL" -> manualTypeCount++;
                default -> throw new IllegalStateException("Unexpected registrationType: " + item.path("registrationType").asText());
            }
            if (item.path("id").asLong() == ignoredFetch.getId()) {
                ignoredFetchNode = item;
            }
        }

        assertThat(pendingCount).isEqualTo(5);
        assertThat(confirmedCount).isEqualTo(3);
        assertThat(ignoredCount).isEqualTo(2);
        assertThat(fetchTypeCount).isEqualTo(6);
        assertThat(syncTypeCount).isEqualTo(3);
        assertThat(manualTypeCount).isEqualTo(1);
        assertThat(ignoredFetchNode).isNotNull();
        assertThat(ignoredFetchNode.path("ignoredReason").asText()).isEqualTo("重复同步批次");
        assertThat(ignoredFetchNode.path("status").asText()).isEqualTo("IGNORED");
        assertThat(items.get(items.size() - 1).path("fetchTime").asText()).startsWith("2026-04-01T09:00");
    }

    private void assertOverview(int pendingFetchCount, int pendingReminderCount, int competitorCount) throws Exception {
        mockMvc.perform(get("/api/bid-results/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.pendingFetchCount").value(pendingFetchCount))
                .andExpect(jsonPath("$.data.pendingReminderCount").value(pendingReminderCount))
                .andExpect(jsonPath("$.data.competitorCount").value(competitorCount));
    }

    private BidResultFetchResult latestFetchResult(BidResultFetchResult.Result result) {
        return fetchResultRepository.findAllByOrderByFetchTimeDesc().stream()
                .filter(item -> item.getRegistrationType() == BidResultFetchResult.RegistrationType.FETCH)
                .filter(item -> item.getResult() == result)
                .findFirst()
                .orElseThrow();
    }

    private long createOldIgnoredResult() {
        Project project = projectRepository.findAll().getFirst();
        BidResultFetchResult entity = fetchResultRepository.save(BidResultFetchResult.builder()
                .source("MANUAL_ARCHIVE")
                .tenderId(project.getTenderId())
                .projectId(project.getId())
                .projectName(project.getName())
                .result(BidResultFetchResult.Result.LOST)
                .fetchTime(LocalDateTime.of(2026, 4, 1, 9, 0))
                .status(BidResultFetchResult.Status.IGNORED)
                .ignoredReason("历史归档记录")
                .registrationType(BidResultFetchResult.RegistrationType.MANUAL)
                .remark("旧数据排序锚点")
                .build());
        return entity.getId();
    }
}
