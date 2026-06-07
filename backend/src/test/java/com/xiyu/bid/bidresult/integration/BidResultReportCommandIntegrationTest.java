package com.xiyu.bid.bidresult.integration;

import com.xiyu.bid.bidresult.entity.BidResultFetchResult;
import com.xiyu.bid.bidresult.entity.BidResultReminder;
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
class BidResultReportCommandIntegrationTest extends AbstractBidResultIntegrationTest {

    private Project project;

    @BeforeEach
    void setUpProject() {
        project = fixtures.createProject("投标结果报告命令集成测试", 83001L);
    }

    @Test
    @WithMockUser(username = "bid-admin", roles = {"ADMIN"})
    void bindAttachmentEndpoint_ShouldBindAnalysisDocumentAndMarkReportReminderUploaded() throws Exception {
        long resultId = createPendingLostFetchResult();
        long documentId = createManualDocument("人工上传分析报告.pdf");

        mockMvc.perform(post("/api/bid-results/{resultId}/attachments/bind", resultId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "documentId": %d,
                                  "attachmentType": "REPORT"
                                }
                                """.formatted(documentId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(resultId))
                .andExpect(jsonPath("$.data.analysisDocumentId").value(documentId));

        BidResultFetchResult result = fetchResultRepository.findById(resultId).orElseThrow();
        assertThat(result.getAnalysisDocumentId()).isEqualTo(documentId);
        assertThat(result.getNoticeDocumentId()).isNull();

        BidResultReminder reminder = reportReminder();
        assertThat(reminder.getStatus()).isEqualTo(BidResultReminder.ReminderStatus.UPLOADED);
        assertThat(reminder.getAttachmentDocumentId()).isEqualTo(documentId);
        assertThat(reminder.getUploadedBy()).isEqualTo(adminUser.getId());
        assertThat(reminder.getUploadedAt()).isNotNull();

        mockMvc.perform(get("/api/bid-results/{id}", resultId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.analysisAttachment.documentId").value(documentId))
                .andExpect(jsonPath("$.data.reminder.status").value("UPLOADED"));
    }

    @Test
    @WithMockUser(username = "bid-admin", roles = {"ADMIN"})
    void confirmWithDataEndpoint_ShouldPersistAnalysisAttachmentReferenceAndKeepReminderConsistent() throws Exception {
        long resultId = createPendingLostFetchResult();
        long documentId = createManualDocument("确认时补录分析报告.pdf");

        mockMvc.perform(post("/api/bid-results/fetch-results/{id}/confirm-with-data", resultId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "remark": "单条确认补录",
                                  "skuCount": 14,
                                  "attachmentDocumentId": %d
                                }
                                """.formatted(documentId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(resultId))
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.analysisDocumentId").value(documentId))
                .andExpect(jsonPath("$.data.remark").value("单条确认补录"))
                .andExpect(jsonPath("$.data.skuCount").value(14));

        BidResultFetchResult result = fetchResultRepository.findById(resultId).orElseThrow();
        assertThat(result.getStatus()).isEqualTo(BidResultFetchResult.Status.CONFIRMED);
        assertThat(result.getAnalysisDocumentId()).isEqualTo(documentId);
        assertThat(result.getConfirmedBy()).isEqualTo(adminUser.getId());
        assertThat(result.getRemark()).isEqualTo("单条确认补录");
        assertThat(result.getSkuCount()).isEqualTo(14);

        BidResultReminder reminder = reportReminder();
        assertThat(reminder.getStatus()).isEqualTo(BidResultReminder.ReminderStatus.PENDING);
        assertThat(reminder.getAttachmentDocumentId()).isEqualTo(documentId);
        assertThat(reminder.getUploadedBy()).isNull();
        assertThat(reminder.getUploadedAt()).isNull();
        assertThat(reminder.getLastReminderComment()).isEqualTo("结果已确认，待上传资料");

        mockMvc.perform(get("/api/bid-results/{id}", resultId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.analysisAttachment.documentId").value(documentId))
                .andExpect(jsonPath("$.data.reminder.status").value("PENDING"));
    }

    private BidResultReminder reportReminder() {
        return fixtures.reminderFor(project.getId(), BidResultReminder.ReminderType.REPORT);
    }

    private long createPendingLostFetchResult() {
        BidResultFetchResult result = fetchResultRepository.save(BidResultFetchResult.builder()
                .source("PUBLIC_FETCH")
                .tenderId(project.getTenderId())
                .projectId(project.getId())
                .projectName(project.getName())
                .result(BidResultFetchResult.Result.LOST)
                .fetchTime(LocalDateTime.of(2026, 4, 18, 11, 0))
                .status(BidResultFetchResult.Status.PENDING)
                .registrationType(BidResultFetchResult.RegistrationType.FETCH)
                .build());
        return result.getId();
    }

    private long createManualDocument(String fileName) throws Exception {
        return httpSupport.createProjectDocument(
                project.getId(),
                "TASK",
                9101L,
                "PROJECT_DELIVERABLE",
                fileName,
                "2MB"
        );
    }
}
