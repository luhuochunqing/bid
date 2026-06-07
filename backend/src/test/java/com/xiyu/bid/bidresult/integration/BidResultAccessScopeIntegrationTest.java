package com.xiyu.bid.bidresult.integration;

import com.xiyu.bid.bidresult.entity.BidResultFetchResult;
import com.xiyu.bid.bidresult.entity.BidResultReminder;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.projectworkflow.entity.ProjectDocument;
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

import java.time.LocalDate;
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
class BidResultAccessScopeIntegrationTest extends AbstractBidResultIntegrationTest {

    private User staffUser;
    private Project visibleProject;
    private Project hiddenProject;
    private BidResultFetchResult visibleResult;
    private BidResultFetchResult hiddenResult;
    private BidResultReminder hiddenReminder;

    @BeforeEach
    void setUpAccessScopedData() {
        staffUser = userRepository.save(User.builder()
                .username("bid-staff")
                .password("XiyuDemo!2026")
                .email("bid-staff@example.com")
                .fullName("投标结果专员")
                .role(User.Role.STAFF)
                .enabled(true)
                .build());
        visibleProject = createProject("可见投标结果项目", 87001L, staffUser.getId(), List.of(staffUser.getId()));
        hiddenProject = createProject("不可见投标结果项目", 87002L, adminUser.getId(), List.of(adminUser.getId()));

        visibleResult = fixtures.saveFetchResult(
                visibleProject,
                BidResultFetchResult.Result.WON,
                BidResultFetchResult.Status.PENDING,
                BidResultFetchResult.RegistrationType.FETCH,
                LocalDateTime.of(2026, 4, 20, 10, 0)
        );
        hiddenResult = fixtures.saveFetchResult(
                hiddenProject,
                BidResultFetchResult.Result.LOST,
                BidResultFetchResult.Status.PENDING,
                BidResultFetchResult.RegistrationType.FETCH,
                LocalDateTime.of(2026, 4, 20, 11, 0)
        );
        fixtures.saveReminder(
                visibleProject,
                BidResultReminder.ReminderType.NOTICE,
                BidResultReminder.ReminderStatus.PENDING,
                LocalDateTime.of(2026, 4, 20, 12, 0),
                "可见提醒",
                visibleResult.getId(),
                null,
                null,
                null
        );
        fixtures.saveReminder(
                hiddenProject,
                BidResultReminder.ReminderType.REPORT,
                BidResultReminder.ReminderStatus.PENDING,
                LocalDateTime.of(2026, 4, 20, 13, 0),
                "不可见提醒",
                hiddenResult.getId(),
                null,
                null,
                null
        );
        hiddenReminder = fixtures.reminderFor(hiddenProject.getId(), BidResultReminder.ReminderType.REPORT);
        fixtures.saveCompetitorWin(visibleProject, 9101L, "可见竞对", LocalDate.of(2026, 4, 20), 5, "服务器");
        fixtures.saveCompetitorWin(hiddenProject, 9102L, "不可见竞对", LocalDate.of(2026, 4, 20), 9, "存储");
    }

    @Test
    @WithMockUser(username = "bid-staff", roles = {"STAFF"})
    void queryEndpoints_ShouldFilterByAccessibleProjectsForNonAdmin() throws Exception {
        mockMvc.perform(get("/api/bid-results/fetch-results"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].projectId").value(visibleProject.getId()));

        mockMvc.perform(get("/api/bid-results/reminders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].projectId").value(visibleProject.getId()));

        mockMvc.perform(get("/api/bid-results/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pendingFetchCount").value(1))
                .andExpect(jsonPath("$.data.pendingReminderCount").value(1))
                .andExpect(jsonPath("$.data.competitorCount").value(1));

        mockMvc.perform(get("/api/bid-results/competitor-report"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].company").value("可见竞对"));
    }

    @Test
    @WithMockUser(username = "bid-staff", roles = {"STAFF"})
    void detailEndpoint_WithInaccessibleProjectResult_ShouldReturnForbidden() throws Exception {
        mockMvc.perform(get("/api/bid-results/{id}", hiddenResult.getId()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @WithMockUser(username = "bid-staff", roles = {"STAFF"})
    void commandEndpoints_WithInaccessibleProjectResult_ShouldReturnForbidden() throws Exception {
        ProjectDocument hiddenDocument = fixtures.saveProjectDocument(
                hiddenProject.getId(),
                "BID_RESULT_ANALYSIS",
                "BID_RESULT",
                hiddenResult.getId(),
                "不可见分析报告.pdf"
        );

        mockMvc.perform(post("/api/bid-results/{id}/update", hiddenResult.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "remark": "不应更新"
                                }
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/bid-results/fetch-results/{id}/confirm-with-data", hiddenResult.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "remark": "不应确认"
                                }
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/bid-results/fetch-results/{id}/ignore", hiddenResult.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "comment": "不应忽略"
                                }
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/bid-results/{id}/attachments/bind", hiddenResult.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "documentId": %d,
                                  "attachmentType": "REPORT"
                                }
                                """.formatted(hiddenDocument.getId())))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/bid-results/reminders/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "resultId": %d,
                                  "comment": "不应提醒"
                                }
                                """.formatted(hiddenResult.getId())))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/bid-results/reminders/{id}/mark-uploaded", hiddenReminder.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "documentId": %d,
                                  "attachmentType": "REPORT"
                                }
                                """.formatted(hiddenDocument.getId())))
                .andExpect(status().isForbidden());

        BidResultFetchResult unchanged = fetchResultRepository.findById(hiddenResult.getId()).orElseThrow();
        assertThat(unchanged.getStatus()).isEqualTo(BidResultFetchResult.Status.PENDING);
        assertThat(unchanged.getIgnoredReason()).isNull();
        assertThat(unchanged.getAnalysisDocumentId()).isNull();
    }

    @Test
    @WithMockUser(username = "bid-staff", roles = {"STAFF"})
    void projectIdCommandEndpoints_WithInaccessibleProject_ShouldReturnForbidden() throws Exception {
        mockMvc.perform(post("/api/bid-results/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectId": %d,
                                  "result": "lost",
                                  "remark": "不应登记"
                                }
                                """.formatted(hiddenProject.getId())))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/bid-results/competitor-wins")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectId": %d,
                                  "competitorName": "不可见新增竞对",
                                  "skuCount": 3,
                                  "category": "网络设备",
                                  "wonAt": "2026-04-20"
                                }
                                """.formatted(hiddenProject.getId())))
                .andExpect(status().isForbidden());
    }

    private Project createProject(String name, Long tenderId, Long managerId, List<Long> teamMembers) {
        return projectRepository.save(Project.builder()
                .name(name)
                .tenderId(tenderId)
                .status(Project.Status.BIDDING)
                .managerId(managerId)
                .teamMembers(teamMembers)
                .startDate(LocalDateTime.of(2026, 4, 1, 9, 0))
                .endDate(LocalDateTime.of(2026, 4, 30, 18, 0))
                .build());
    }
}
