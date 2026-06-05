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
class BidResultAnalysisClosureIntegrationTest extends AbstractBidResultIntegrationTest {

    private Project project;

    @BeforeEach
    void setUpProject() {
        project = fixtures.createProject("投标结果分析闭环集成测试", 82001L);
    }

    @Test
    @WithMockUser(username = "bid-admin", roles = {"ADMIN"})
    void analysisDocumentUpload_ShouldAutoBindToLostBidResultAndMarkReportReminderUploaded() throws Exception {
        long resultId = registerLostResult(project);
        long documentId = httpSupport.createBidResultDocument(
                project.getId(),
                resultId,
                "BID_RESULT_ANALYSIS",
                "分析报告.pdf"
        );

        BidResultFetchResult result = fetchResultRepository.findById(resultId).orElseThrow();
        assertThat(result.getAnalysisDocumentId()).isEqualTo(documentId);
        assertThat(result.getNoticeDocumentId()).isNull();

        BidResultReminder reminder = reportReminder(project.getId());
        assertThat(reminder.getStatus()).isEqualTo(BidResultReminder.ReminderStatus.UPLOADED);
        assertThat(reminder.getAttachmentDocumentId()).isEqualTo(documentId);
        assertThat(reminder.getUploadedBy()).isEqualTo(adminUser.getId());
        assertThat(reminder.getUploadedAt()).isNotNull();

        mockMvc.perform(get("/api/bid-results/{id}", resultId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fetchResult.id").value(resultId))
                .andExpect(jsonPath("$.data.analysisAttachment.documentId").value(documentId))
                .andExpect(jsonPath("$.data.reminder.status").value("UPLOADED"));
    }

    @Test
    @WithMockUser(username = "bid-admin", roles = {"ADMIN"})
    void analysisDocumentDeletion_ShouldClearAnalysisAttachmentAndRevertReportReminderState() throws Exception {
        long resultId = registerLostResult(project);
        long documentId = httpSupport.createBidResultDocument(
                project.getId(),
                resultId,
                "BID_RESULT_ANALYSIS",
                "分析报告.pdf"
        );

        mockMvc.perform(delete("/api/projects/{projectId}/documents/{documentId}", project.getId(), documentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        BidResultFetchResult result = fetchResultRepository.findById(resultId).orElseThrow();
        assertThat(result.getAnalysisDocumentId()).isNull();

        BidResultReminder reminder = reportReminder(project.getId());
        assertThat(reminder.getStatus()).isEqualTo(BidResultReminder.ReminderStatus.PENDING);
        assertThat(reminder.getAttachmentDocumentId()).isNull();
        assertThat(reminder.getUploadedBy()).isNull();
        assertThat(reminder.getUploadedAt()).isNull();

        mockMvc.perform(get("/api/bid-results/{id}", resultId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.analysisAttachment").doesNotExist())
                .andExpect(jsonPath("$.data.reminder.status").value("PENDING"));
    }

    @Test
    @WithMockUser(username = "bid-admin", roles = {"ADMIN"})
    void batchConfirmation_ShouldPreserveUploadedAnalysisAttachmentStateAndCreatePendingReminderForPlainResult()
            throws Exception {
        Project secondProject = fixtures.createProject("投标结果批量确认分析闭环", 82002L);

        long uploadedResultId = createPendingLostFetchResult(project);
        long plainResultId = createPendingLostFetchResult(secondProject);
        long documentId = httpSupport.createBidResultDocument(
                project.getId(),
                uploadedResultId,
                "BID_RESULT_ANALYSIS",
                "批量确认前分析报告.pdf"
        );

        mockMvc.perform(post("/api/bid-results/fetch-results/confirm-batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "ids": [%d, %d],
                                  "comment": "批量确认补录"
                                }
                                """.formatted(uploadedResultId, plainResultId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.affectedCount").value(2));

        BidResultFetchResult uploadedResult = fetchResultRepository.findById(uploadedResultId).orElseThrow();
        assertThat(uploadedResult.getStatus()).isEqualTo(BidResultFetchResult.Status.CONFIRMED);
        assertThat(uploadedResult.getAnalysisDocumentId()).isEqualTo(documentId);
        assertThat(uploadedResult.getConfirmedBy()).isEqualTo(adminUser.getId());

        BidResultReminder uploadedReminder = reportReminder(project.getId());
        assertThat(uploadedReminder.getStatus()).isEqualTo(BidResultReminder.ReminderStatus.UPLOADED);
        assertThat(uploadedReminder.getAttachmentDocumentId()).isEqualTo(documentId);
        assertThat(uploadedReminder.getUploadedBy()).isEqualTo(adminUser.getId());
        assertThat(uploadedReminder.getUploadedAt()).isNotNull();
        assertThat(uploadedReminder.getLastReminderComment()).isEqualTo("结果已确认，待上传资料");

        BidResultFetchResult plainResult = fetchResultRepository.findById(plainResultId).orElseThrow();
        assertThat(plainResult.getStatus()).isEqualTo(BidResultFetchResult.Status.CONFIRMED);
        assertThat(plainResult.getAnalysisDocumentId()).isNull();
        assertThat(plainResult.getConfirmedBy()).isEqualTo(adminUser.getId());

        BidResultReminder plainReminder = reportReminder(secondProject.getId());
        assertThat(plainReminder.getStatus()).isEqualTo(BidResultReminder.ReminderStatus.PENDING);
        assertThat(plainReminder.getAttachmentDocumentId()).isNull();
        assertThat(plainReminder.getUploadedBy()).isNull();
        assertThat(plainReminder.getUploadedAt()).isNull();
        assertThat(plainReminder.getLastReminderComment()).isEqualTo("结果已确认，待上传资料");

        mockMvc.perform(get("/api/bid-results/{id}", uploadedResultId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.analysisAttachment.documentId").value(documentId))
                .andExpect(jsonPath("$.data.reminder.status").value("UPLOADED"));

        mockMvc.perform(get("/api/bid-results/{id}", plainResultId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.analysisAttachment").doesNotExist())
                .andExpect(jsonPath("$.data.reminder.status").value("PENDING"));
    }

    private BidResultReminder reportReminder(Long projectId) {
        return fixtures.reminderFor(projectId, BidResultReminder.ReminderType.REPORT);
    }

    private long registerLostResult(Project targetProject) throws Exception {
        String response = mockMvc.perform(post("/api/bid-results/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectId": %d,
                                  "result": "lost",
                                  "remark": "分析报告集成测试",
                                  "skuCount": 6
                                }
                                """.formatted(targetProject.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.projectId").value(targetProject.getId()))
                .andExpect(jsonPath("$.data.result").value("LOST"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        return httpSupport.readDataNode(response).path("id").asLong();
    }

    private long createPendingLostFetchResult(Project targetProject) {
        BidResultFetchResult result = fetchResultRepository.save(BidResultFetchResult.builder()
                .source("PUBLIC_FETCH")
                .tenderId(targetProject.getTenderId())
                .projectId(targetProject.getId())
                .projectName(targetProject.getName())
                .result(BidResultFetchResult.Result.LOST)
                .fetchTime(LocalDateTime.of(2026, 4, 18, 11, 0))
                .status(BidResultFetchResult.Status.PENDING)
                .registrationType(BidResultFetchResult.RegistrationType.FETCH)
                .build());
        return result.getId();
    }
}
