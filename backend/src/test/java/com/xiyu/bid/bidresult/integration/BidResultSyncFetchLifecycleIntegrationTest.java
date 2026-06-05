package com.xiyu.bid.bidresult.integration;

import com.xiyu.bid.bidresult.entity.BidResultFetchResult;
import com.xiyu.bid.bidresult.entity.BidResultReminder;
import com.xiyu.bid.bidresult.entity.BidResultSyncLog;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.main.allow-bean-definition-overriding=true")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ActiveProfiles("test")
@Import(NoOpPasswordEncryptionTestConfig.class)
class BidResultSyncFetchLifecycleIntegrationTest extends AbstractBidResultIntegrationTest {

    @BeforeEach
    void setUpProjects() {
        fixtures.createProject("外部同步闭环项目-A", 84001L);
        fixtures.createProject("外部同步闭环项目-B", 84002L);
        fixtures.createProject("外部同步闭环项目-C", 84003L);
    }

    @Test
    @WithMockUser(username = "bid-admin", roles = {"ADMIN"})
    void fetchEndpoint_ShouldPersistPendingResultsAndFetchLog() throws Exception {
        mockMvc.perform(post("/api/bid-results/fetch"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.affectedCount").value(3))
                .andExpect(jsonPath("$.data.message").value("已完成公开投标信息同步 (Mock)"));

        List<BidResultFetchResult> fetchResults = fetchResultRepository.findAllByOrderByFetchTimeDesc().stream()
                .filter(result -> result.getRegistrationType() == BidResultFetchResult.RegistrationType.FETCH)
                .toList();
        assertThat(fetchResults).hasSize(3);
        assertThat(fetchResults).allSatisfy(result -> {
            assertThat(result.getStatus()).isEqualTo(BidResultFetchResult.Status.PENDING);
            assertThat(result.getSource()).isEqualTo("公开信息");
            assertThat(result.getAmount()).isNull();
            assertThat(result.getConfirmedAt()).isNull();
            assertThat(result.getConfirmedBy()).isNull();
            assertThat(result.getRemark()).isEqualTo("公开同步待确认");
        });

        BidResultSyncLog log = syncLogRepository.findFirstByOperationTypeOrderByCreatedAtDesc(
                BidResultSyncLog.OperationType.FETCH
        ).orElseThrow();
        assertThat(log.getSource()).isEqualTo("public-mock");
        assertThat(log.getAffectedCount()).isEqualTo(3);
        assertThat(log.getOperatorId()).isEqualTo(adminUser.getId());
        assertThat(log.getOperatorName()).isEqualTo(adminUser.getFullName());
    }

    @Test
    @WithMockUser(username = "bid-admin", roles = {"ADMIN"})
    void fetchedResults_ShouldSupportIgnoreAndConfirmThenAutoBindAnalysisUpload() throws Exception {
        mockMvc.perform(post("/api/bid-results/fetch"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.affectedCount").value(3));

        List<BidResultFetchResult> fetchResults = fetchResultRepository.findAllByOrderByFetchTimeDesc().stream()
                .filter(result -> result.getRegistrationType() == BidResultFetchResult.RegistrationType.FETCH)
                .toList();
        BidResultFetchResult confirmTarget = fetchResults.stream()
                .filter(result -> result.getResult() == BidResultFetchResult.Result.LOST)
                .findFirst()
                .orElseThrow();
        BidResultFetchResult ignoredTarget = fetchResults.stream()
                .filter(result -> !result.getId().equals(confirmTarget.getId()))
                .findFirst()
                .orElseThrow();

        mockMvc.perform(post("/api/bid-results/fetch-results/{id}/ignore", ignoredTarget.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "comment": "公开信息重复"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(post("/api/bid-results/fetch-results/{id}/confirm-with-data", confirmTarget.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "remark": "外部结果已人工确认",
                                  "skuCount": 9
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(confirmTarget.getId()))
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.skuCount").value(9));

        BidResultFetchResult ignoredResult = fetchResultRepository.findById(ignoredTarget.getId()).orElseThrow();
        assertThat(ignoredResult.getStatus()).isEqualTo(BidResultFetchResult.Status.IGNORED);
        assertThat(ignoredResult.getIgnoredReason()).isEqualTo("公开信息重复");
        assertThat(reminderRepository.findFirstByProjectIdAndReminderTypeOrderByRemindTimeDesc(
                ignoredResult.getProjectId(),
                fixtures.reminderTypeFor(ignoredResult.getResult())
        )).isEmpty();

        BidResultFetchResult confirmedResult = fetchResultRepository.findById(confirmTarget.getId()).orElseThrow();
        assertThat(confirmedResult.getStatus()).isEqualTo(BidResultFetchResult.Status.CONFIRMED);
        assertThat(confirmedResult.getConfirmedBy()).isEqualTo(adminUser.getId());
        assertThat(confirmedResult.getRemark()).isEqualTo("外部结果已人工确认");
        assertThat(confirmedResult.getSkuCount()).isEqualTo(9);
        assertThat(confirmedResult.getAnalysisDocumentId()).isNull();

        BidResultReminder pendingReminder = fixtures.reminderFor(
                confirmedResult.getProjectId(),
                BidResultReminder.ReminderType.REPORT
        );
        assertThat(pendingReminder.getStatus()).isEqualTo(BidResultReminder.ReminderStatus.PENDING);
        assertThat(pendingReminder.getAttachmentDocumentId()).isNull();
        assertThat(pendingReminder.getLastReminderComment()).isEqualTo("结果已确认，待上传资料");

        long documentId = httpSupport.createBidResultDocument(
                confirmedResult.getProjectId(),
                confirmedResult.getId(),
                "BID_RESULT_ANALYSIS",
                "同步后分析报告.pdf"
        );

        BidResultFetchResult uploadedResult = fetchResultRepository.findById(confirmTarget.getId()).orElseThrow();
        assertThat(uploadedResult.getAnalysisDocumentId()).isEqualTo(documentId);

        BidResultReminder uploadedReminder = fixtures.reminderFor(
                confirmedResult.getProjectId(),
                BidResultReminder.ReminderType.REPORT
        );
        assertThat(uploadedReminder.getStatus()).isEqualTo(BidResultReminder.ReminderStatus.UPLOADED);
        assertThat(uploadedReminder.getAttachmentDocumentId()).isEqualTo(documentId);
        assertThat(uploadedReminder.getUploadedBy()).isEqualTo(adminUser.getId());
        assertThat(uploadedReminder.getUploadedAt()).isNotNull();

        mockMvc.perform(get("/api/bid-results/{id}", confirmTarget.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.analysisAttachment.documentId").value(documentId))
                .andExpect(jsonPath("$.data.reminder.status").value("UPLOADED"));
    }

    @Test
    @WithMockUser(username = "bid-admin", roles = {"ADMIN"})
    void syncEndpoint_ShouldPersistConfirmedResultsAndAllowNoticeUpload() throws Exception {
        mockMvc.perform(post("/api/bid-results/sync"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.affectedCount").value(3))
                .andExpect(jsonPath("$.data.message").value("已同步内部 ERP/CRM 数据 (Mock)"));

        List<BidResultFetchResult> syncResults = fetchResultRepository.findAllByOrderByFetchTimeDesc().stream()
                .filter(result -> result.getRegistrationType() == BidResultFetchResult.RegistrationType.SYNC)
                .toList();
        BidResultFetchResult wonResult = syncResults.stream()
                .filter(result -> result.getResult() == BidResultFetchResult.Result.WON)
                .findFirst()
                .orElseThrow();

        assertThat(syncResults).hasSize(3);
        assertThat(syncResults).allSatisfy(result -> {
            assertThat(result.getStatus()).isEqualTo(BidResultFetchResult.Status.CONFIRMED);
            assertThat(result.getSource()).isEqualTo("内部系统");
            assertThat(result.getAmount()).isNotNull();
            assertThat(result.getConfirmedAt()).isNotNull();
            assertThat(result.getRemark()).isEqualTo("内部同步结果");
        });
        assertThat(reminderRepository.findFirstByProjectIdAndReminderTypeOrderByRemindTimeDesc(
                wonResult.getProjectId(),
                BidResultReminder.ReminderType.NOTICE
        )).isEmpty();

        BidResultSyncLog log = syncLogRepository.findFirstByOperationTypeOrderByCreatedAtDesc(
                BidResultSyncLog.OperationType.SYNC
        ).orElseThrow();
        assertThat(log.getSource()).isEqualTo("internal-mock");
        assertThat(log.getAffectedCount()).isEqualTo(3);
        assertThat(log.getOperatorName()).isEqualTo(adminUser.getFullName());

        long documentId = httpSupport.createBidResultDocument(
                wonResult.getProjectId(),
                wonResult.getId(),
                "BID_RESULT_NOTICE",
                "同步后中标通知书.pdf"
        );

        BidResultFetchResult uploadedResult = fetchResultRepository.findById(wonResult.getId()).orElseThrow();
        assertThat(uploadedResult.getNoticeDocumentId()).isEqualTo(documentId);

        BidResultReminder reminder = fixtures.reminderFor(
                wonResult.getProjectId(),
                BidResultReminder.ReminderType.NOTICE
        );
        assertThat(reminder.getStatus()).isEqualTo(BidResultReminder.ReminderStatus.UPLOADED);
        assertThat(reminder.getAttachmentDocumentId()).isEqualTo(documentId);
        assertThat(reminder.getUploadedBy()).isEqualTo(adminUser.getId());

        mockMvc.perform(get("/api/bid-results/{id}", wonResult.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.noticeAttachment.documentId").value(documentId))
                .andExpect(jsonPath("$.data.reminder.status").value("UPLOADED"));
    }
}
