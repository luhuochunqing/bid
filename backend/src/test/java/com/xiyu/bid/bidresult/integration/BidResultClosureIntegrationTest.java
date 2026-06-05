package com.xiyu.bid.bidresult.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.xiyu.bid.bidresult.entity.BidResultFetchResult;
import com.xiyu.bid.bidresult.entity.BidResultReminder;
import com.xiyu.bid.bidresult.entity.CompetitorWinRecord;
import com.xiyu.bid.competitionintel.entity.Competitor;
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

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.main.allow-bean-definition-overriding=true")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ActiveProfiles("test")
@Import(NoOpPasswordEncryptionTestConfig.class)
class BidResultClosureIntegrationTest extends AbstractBidResultIntegrationTest {

    private Project project;

    @BeforeEach
    void setUpProject() {
        project = fixtures.createProject("投标结果闭环集成测试", 81001L);
    }

    @Test
    @WithMockUser(username = "bid-admin", roles = {"ADMIN"})
    void linkedDocumentUpload_ShouldAutoBindToBidResultAndMarkReminderUploaded() throws Exception {
        long resultId = registerWonResult();
        long documentId = httpSupport.createBidResultDocument(
                project.getId(),
                resultId,
                "BID_RESULT_NOTICE",
                "中标通知书.pdf"
        );

        BidResultFetchResult result = fetchResultRepository.findById(resultId).orElseThrow();
        assertThat(result.getNoticeDocumentId()).isEqualTo(documentId);
        assertThat(result.getAnalysisDocumentId()).isNull();

        BidResultReminder reminder = fixtures.reminderFor(project.getId(), BidResultReminder.ReminderType.NOTICE);
        assertThat(reminder.getStatus()).isEqualTo(BidResultReminder.ReminderStatus.UPLOADED);
        assertThat(reminder.getAttachmentDocumentId()).isEqualTo(documentId);
        assertThat(reminder.getUploadedBy()).isEqualTo(adminUser.getId());
        assertThat(reminder.getUploadedAt()).isNotNull();

        mockMvc.perform(get("/api/bid-results/{id}", resultId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fetchResult.id").value(resultId))
                .andExpect(jsonPath("$.data.noticeAttachment.documentId").value(documentId))
                .andExpect(jsonPath("$.data.reminder.status").value("UPLOADED"));
    }

    @Test
    @WithMockUser(username = "bid-admin", roles = {"ADMIN"})
    void linkedDocumentDeletion_ShouldClearAttachmentAndRevertReminderState() throws Exception {
        long resultId = registerWonResult();
        long documentId = httpSupport.createBidResultDocument(
                project.getId(),
                resultId,
                "BID_RESULT_NOTICE",
                "中标通知书.pdf"
        );

        mockMvc.perform(delete("/api/projects/{projectId}/documents/{documentId}", project.getId(), documentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        BidResultFetchResult result = fetchResultRepository.findById(resultId).orElseThrow();
        assertThat(result.getNoticeDocumentId()).isNull();

        BidResultReminder reminder = fixtures.reminderFor(project.getId(), BidResultReminder.ReminderType.NOTICE);
        assertThat(reminder.getStatus()).isEqualTo(BidResultReminder.ReminderStatus.PENDING);
        assertThat(reminder.getAttachmentDocumentId()).isNull();
        assertThat(reminder.getUploadedBy()).isNull();
        assertThat(reminder.getUploadedAt()).isNull();

        mockMvc.perform(get("/api/bid-results/{id}", resultId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.noticeAttachment").doesNotExist())
                .andExpect(jsonPath("$.data.reminder.status").value("PENDING"));
    }

    @Test
    @WithMockUser(username = "bid-admin", roles = {"ADMIN"})
    void competitorWinCreation_ShouldAutoCreateAndReuseCompetitorProfileByName() throws Exception {
        String firstResponse = mockMvc.perform(post("/api/bid-results/competitor-wins")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectId": %d,
                                  "competitorName": "自动建档竞对A",
                                  "skuCount": 12,
                                  "category": "服务器",
                                  "discount": "92折",
                                  "paymentTerms": "月结30天",
                                  "wonAt": "2026-04-18",
                                  "amount": 880000,
                                  "notes": "第一次自动建档"
                                }
                                """.formatted(project.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.competitorName").value("自动建档竞对A"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode firstJson = objectMapper.readTree(firstResponse);
        long firstCompetitorId = firstJson.path("data").path("competitorId").asLong();

        String secondResponse = mockMvc.perform(post("/api/bid-results/competitor-wins")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectId": %d,
                                  "competitorName": "自动建档竞对A",
                                  "skuCount": 24,
                                  "category": "存储",
                                  "discount": "90折",
                                  "paymentTerms": "季度结",
                                  "wonAt": "2026-04-20",
                                  "amount": 990000,
                                  "notes": "第二次复用档案"
                                }
                                """.formatted(project.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.competitorName").value("自动建档竞对A"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode secondJson = objectMapper.readTree(secondResponse);
        long secondCompetitorId = secondJson.path("data").path("competitorId").asLong();

        Competitor competitor = competitorRepository.findByName("自动建档竞对A").orElseThrow();
        List<CompetitorWinRecord> records = competitorWinRecordRepository.findByProjectIdOrderByWonAtDesc(project.getId());

        assertThat(firstCompetitorId).isEqualTo(competitor.getId());
        assertThat(secondCompetitorId).isEqualTo(competitor.getId());
        assertThat(competitorRepository.findAll().stream()
                .filter(item -> "自动建档竞对A".equals(item.getName())))
                .hasSize(1);
        assertThat(records).hasSize(2);
        assertThat(records).allMatch(record -> record.getCompetitorId().equals(competitor.getId()));

        mockMvc.perform(get("/api/bid-results/competitor-report"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].company").value("自动建档竞对A"));
    }

    private long registerWonResult() throws Exception {
        String response = mockMvc.perform(post("/api/bid-results/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectId": %d,
                                  "result": "won",
                                  "amount": 1280000,
                                  "contractStartDate": "2026-05-01",
                                  "contractEndDate": "2027-04-30",
                                  "contractDurationMonths": 12,
                                  "remark": "集成测试登记",
                                  "skuCount": 18
                                }
                                """.formatted(project.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.projectId").value(project.getId()))
                .andExpect(jsonPath("$.data.result").value("WON"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        return httpSupport.readDataNode(response).path("id").asLong();
    }
}
