package com.xiyu.bid.bidresult.integration;

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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.main.allow-bean-definition-overriding=true")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ActiveProfiles("test")
@Import(NoOpPasswordEncryptionTestConfig.class)
class BidResultCommandFailureIntegrationTest extends AbstractBidResultIntegrationTest {

    private Project project;

    @BeforeEach
    void setUpProject() {
        project = fixtures.createProject("投标结果命令失败集成测试", 84001L);
    }

    @Test
    @WithMockUser(username = "bid-admin", roles = {"ADMIN"})
    void bindAttachmentEndpoint_WithMissingParameters_ShouldReturnBadRequest() throws Exception {
        long resultId = createFetchResult(BidResultFetchResult.Status.PENDING);

        mockMvc.perform(post("/api/bid-results/{resultId}/attachments/bind", resultId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "documentId": null,
                                  "attachmentType": null
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value("附件绑定参数不完整"));
    }

    @Test
    @WithMockUser(username = "bid-admin", roles = {"ADMIN"})
    void bindAttachmentEndpoint_WithMissingDocument_ShouldReturnNotFound() throws Exception {
        long resultId = createFetchResult(BidResultFetchResult.Status.PENDING);

        mockMvc.perform(post("/api/bid-results/{resultId}/attachments/bind", resultId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "documentId": 999999,
                                  "attachmentType": "REPORT"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.msg").value("请求的资源不存在"));
    }

    @Test
    @WithMockUser(username = "bid-admin", roles = {"ADMIN"})
    void confirmWithDataEndpoint_WithIgnoredResult_ShouldReturnBadRequest() throws Exception {
        long resultId = createFetchResult(BidResultFetchResult.Status.IGNORED);

        mockMvc.perform(post("/api/bid-results/fetch-results/{id}/confirm-with-data", resultId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "remark": "不应该被确认"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value("已忽略的记录不能确认"));
    }

    @Test
    @WithMockUser(username = "bid-admin", roles = {"ADMIN"})
    void confirmWithDataEndpoint_WithWonResultButMissingAmount_ShouldReturnBadRequest() throws Exception {
        long resultId = createFetchResult(BidResultFetchResult.Status.PENDING);

        mockMvc.perform(post("/api/bid-results/fetch-results/{id}/confirm-with-data", resultId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "result": "won",
                                  "remark": "缺少中标金额"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value("中标时必须填写金额且大于 0"));
    }

    @Test
    @WithMockUser(username = "bid-admin", roles = {"ADMIN"})
    void ignoreEndpoint_WithBlankReason_ShouldReturnBadRequest() throws Exception {
        long resultId = createFetchResult(BidResultFetchResult.Status.PENDING);

        mockMvc.perform(post("/api/bid-results/fetch-results/{id}/ignore", resultId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "comment": "   "
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value("忽略原因不能为空"));
    }

    @Test
    @WithMockUser(username = "bid-admin", roles = {"ADMIN"})
    void confirmBatchEndpoint_WithMoreThanTwoHundredIds_ShouldReturnBadRequest() throws Exception {
        List<Long> ids = createFetchResults(201, BidResultFetchResult.Status.PENDING);
        String idList = ids.stream()
                .map(String::valueOf)
                .reduce((left, right) -> left + "," + right)
                .orElse("");

        mockMvc.perform(post("/api/bid-results/fetch-results/confirm-batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "ids": [%s],
                                  "comment": "批量确认越界"
                                }
                                """.formatted(idList)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value("批量数量不得超过 200"));
    }

    @Test
    @WithMockUser(username = "bid-admin", roles = {"ADMIN"})
    void confirmBatchEndpoint_WithIgnoredResultIncluded_ShouldRollbackPreviouslyConfirmedItems() throws Exception {
        long pendingId = createFetchResult(BidResultFetchResult.Status.PENDING);
        long ignoredId = createFetchResult(BidResultFetchResult.Status.IGNORED);

        mockMvc.perform(post("/api/bid-results/fetch-results/confirm-batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "ids": [%d, %d],
                                  "comment": "混合批量确认"
                                }
                                """.formatted(pendingId, ignoredId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value("已忽略的记录不能确认"));

        BidResultFetchResult pending = fetchResultRepository.findById(pendingId).orElseThrow();
        BidResultFetchResult ignored = fetchResultRepository.findById(ignoredId).orElseThrow();

        assertThat(pending.getStatus()).isEqualTo(BidResultFetchResult.Status.PENDING);
        assertThat(pending.getConfirmedAt()).isNull();
        assertThat(pending.getConfirmedBy()).isNull();
        assertThat(pending.getRemark()).isNull();
        assertThat(reminderRepository.count()).isZero();

        assertThat(ignored.getStatus()).isEqualTo(BidResultFetchResult.Status.IGNORED);
        assertThat(ignored.getIgnoredReason()).isEqualTo("重复公开信息");
    }

    @Test
    @WithMockUser(username = "bid-admin", roles = {"ADMIN"})
    void confirmBatchEndpoint_WithMissingResultIncluded_ShouldRollbackPreviouslyConfirmedItems() throws Exception {
        long pendingId = createFetchResult(BidResultFetchResult.Status.PENDING);

        mockMvc.perform(post("/api/bid-results/fetch-results/confirm-batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "ids": [%d, 999999],
                                  "comment": "包含不存在记录"
                                }
                                """.formatted(pendingId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.msg").value("请求的资源不存在"));

        BidResultFetchResult pending = fetchResultRepository.findById(pendingId).orElseThrow();
        assertThat(pending.getStatus()).isEqualTo(BidResultFetchResult.Status.PENDING);
        assertThat(pending.getConfirmedAt()).isNull();
        assertThat(pending.getConfirmedBy()).isNull();
        assertThat(pending.getRemark()).isNull();
        assertThat(reminderRepository.count()).isZero();
    }

    private long createFetchResult(BidResultFetchResult.Status status) {
        BidResultFetchResult result = fetchResultRepository.save(BidResultFetchResult.builder()
                .source("PUBLIC_FETCH")
                .tenderId(project.getTenderId())
                .projectId(project.getId())
                .projectName(project.getName())
                .result(BidResultFetchResult.Result.LOST)
                .fetchTime(LocalDateTime.of(2026, 4, 18, 11, 0))
                .status(status)
                .ignoredReason(status == BidResultFetchResult.Status.IGNORED ? "重复公开信息" : null)
                .registrationType(BidResultFetchResult.RegistrationType.FETCH)
                .build());
        return result.getId();
    }

    private List<Long> createFetchResults(int count, BidResultFetchResult.Status status) {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            ids.add(createFetchResult(status));
        }
        return ids;
    }
}
