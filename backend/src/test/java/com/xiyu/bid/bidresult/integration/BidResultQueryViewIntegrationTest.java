package com.xiyu.bid.bidresult.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.xiyu.bid.bidresult.entity.BidResultFetchResult;
import com.xiyu.bid.bidresult.entity.BidResultReminder;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.projectworkflow.entity.ProjectDocument;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import com.xiyu.bid.support.NoOpPasswordEncryptionTestConfig;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.main.allow-bean-definition-overriding=true")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ActiveProfiles("test")
@Import(NoOpPasswordEncryptionTestConfig.class)
class BidResultQueryViewIntegrationTest extends AbstractBidResultIntegrationTest {

    @Test
    @WithMockUser(username = "bid-admin", roles = {"ADMIN"})
    void remindersEndpoint_ShouldReturnAllReminderStatesInRemindTimeDescOrder() throws Exception {
        Project firstProject = fixtures.createProject("提醒视图项目-A", 86001L);
        Project secondProject = fixtures.createProject("提醒视图项目-B", 86002L);
        Project thirdProject = fixtures.createProject("提醒视图项目-C", 86003L);

        long remindedResultId = fixtures.saveFetchResult(
                firstProject,
                BidResultFetchResult.Result.LOST,
                BidResultFetchResult.Status.CONFIRMED,
                BidResultFetchResult.RegistrationType.FETCH,
                LocalDateTime.of(2026, 4, 18, 8, 0)
        ).getId();
        long pendingResultId = fixtures.saveFetchResult(
                secondProject,
                BidResultFetchResult.Result.WON,
                BidResultFetchResult.Status.CONFIRMED,
                BidResultFetchResult.RegistrationType.SYNC,
                LocalDateTime.of(2026, 4, 18, 8, 30)
        ).getId();
        long uploadedResultId = fixtures.saveFetchResult(
                thirdProject,
                BidResultFetchResult.Result.WON,
                BidResultFetchResult.Status.CONFIRMED,
                BidResultFetchResult.RegistrationType.MANUAL,
                LocalDateTime.of(2026, 4, 18, 9, 0)
        ).getId();

        fixtures.saveReminder(
                firstProject,
                BidResultReminder.ReminderType.REPORT,
                BidResultReminder.ReminderStatus.REMINDED,
                LocalDateTime.of(2026, 4, 18, 9, 0),
                "第一次催传",
                remindedResultId,
                null,
                null,
                null
        );
        fixtures.saveReminder(
                secondProject,
                BidResultReminder.ReminderType.NOTICE,
                BidResultReminder.ReminderStatus.PENDING,
                LocalDateTime.of(2026, 4, 18, 10, 0),
                "待上传资料",
                pendingResultId,
                null,
                null,
                null
        );
        fixtures.saveReminder(
                thirdProject,
                BidResultReminder.ReminderType.NOTICE,
                BidResultReminder.ReminderStatus.UPLOADED,
                LocalDateTime.of(2026, 4, 18, 11, 0),
                "资料已上传",
                uploadedResultId,
                901L,
                adminUser.getId(),
                LocalDateTime.of(2026, 4, 18, 11, 0)
        );

        String response = mockMvc.perform(get("/api/bid-results/reminders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].status").value("UPLOADED"))
                .andExpect(jsonPath("$.data[1].status").value("PENDING"))
                .andExpect(jsonPath("$.data[2].status").value("REMINDED"))
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        JsonNode items = objectMapper.readTree(response).path("data");
        LocalDateTime firstTime = LocalDateTime.parse(items.get(0).path("remindTime").asText());
        LocalDateTime secondTime = LocalDateTime.parse(items.get(1).path("remindTime").asText());
        LocalDateTime thirdTime = LocalDateTime.parse(items.get(2).path("remindTime").asText());

        assertThat(firstTime).isAfter(secondTime);
        assertThat(secondTime).isAfter(thirdTime);
        assertThat(items.get(0).path("attachmentDocumentId").asLong()).isEqualTo(901L);
        assertThat(items.get(0).path("uploadedBy").asLong()).isEqualTo(adminUser.getId());
        assertThat(items.get(2).path("lastReminderComment").asText()).isEqualTo("第一次催传");
        assertThat(items.get(2).path("projectName").asText()).isEqualTo("提醒视图项目-A");
    }

    @Test
    @WithMockUser(username = "bid-admin", roles = {"ADMIN"})
    void detailEndpoint_ShouldAssembleReminderRequiredAttachmentAndCompetitorWins() throws Exception {
        Project project = fixtures.createProject("详情视图项目", 86101L);
        BidResultFetchResult result = fixtures.saveFetchResult(
                project,
                BidResultFetchResult.Result.WON,
                BidResultFetchResult.Status.CONFIRMED,
                BidResultFetchResult.RegistrationType.MANUAL,
                LocalDateTime.of(2026, 4, 18, 12, 0)
        );
        ProjectDocument notice = fixtures.saveProjectDocument(
                project.getId(),
                "BID_RESULT_NOTICE",
                "BID_RESULT",
                result.getId(),
                "中标通知书.pdf"
        );
        result.setNoticeDocumentId(notice.getId());
        fetchResultRepository.save(result);

        fixtures.saveReminder(
                project,
                BidResultReminder.ReminderType.NOTICE,
                BidResultReminder.ReminderStatus.UPLOADED,
                LocalDateTime.of(2026, 4, 18, 12, 30),
                "资料已上传",
                result.getId(),
                notice.getId(),
                adminUser.getId(),
                LocalDateTime.of(2026, 4, 18, 12, 30)
        );
        fixtures.saveCompetitorWin(project, 3001L, "竞对甲", LocalDate.of(2026, 4, 20), 12, "服务器");
        fixtures.saveCompetitorWin(project, 3002L, "竞对乙", LocalDate.of(2026, 4, 18), 6, "存储");

        mockMvc.perform(get("/api/bid-results/{id}", result.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.fetchResult.id").value(result.getId()))
                .andExpect(jsonPath("$.data.fetchResult.result").value("WON"))
                .andExpect(jsonPath("$.data.fetchResult.noticeDocumentId").value(notice.getId()))
                .andExpect(jsonPath("$.data.reminder.status").value("UPLOADED"))
                .andExpect(jsonPath("$.data.reminder.attachmentDocumentId").value(notice.getId()))
                .andExpect(jsonPath("$.data.requiredAttachment.attachmentType").value("NOTICE"))
                .andExpect(jsonPath("$.data.requiredAttachment.reference").value("notice"))
                .andExpect(jsonPath("$.data.noticeAttachment.documentId").value(notice.getId()))
                .andExpect(jsonPath("$.data.noticeAttachment.name").value("中标通知书.pdf"))
                .andExpect(jsonPath("$.data.noticeAttachment.reference").value("project-document:" + notice.getId()))
                .andExpect(jsonPath("$.data.analysisAttachment").doesNotExist())
                .andExpect(jsonPath("$.data.competitorWins.length()").value(2))
                .andExpect(jsonPath("$.data.competitorWins[0].competitorName").value("竞对甲"))
                .andExpect(jsonPath("$.data.competitorWins[1].competitorName").value("竞对乙"))
                .andExpect(jsonPath("$.data.competitorWins[0].category").value("服务器"))
                .andExpect(jsonPath("$.data.competitorWins[1].category").value("存储"));
    }
}
