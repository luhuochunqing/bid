// Input: 模拟 HTTP 请求 (GET/PUT/POST submit) 触发新增的 V119 评估端点
// Output: 验证新端点的契约 (200/400/403/404/409 + DTO 字段)
// Pos: backend test source - 单元级 MockMvc (standalone)
// Phase: TDD Phase 2 RED — 这些测试当前 MUST 失败，由 Phase 3 实现端点后转绿
package com.xiyu.bid.tender.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.exception.GlobalExceptionHandler;
import com.xiyu.bid.service.AuthService;
import com.xiyu.bid.task.service.TaskService;
import com.xiyu.bid.tender.dto.TenderEvaluationDTO;
import com.xiyu.bid.tender.dto.TenderEvaluationSubmitRequest;
import com.xiyu.bid.tender.entity.TenderEvaluation.BidRecommendation;
import com.xiyu.bid.tender.entity.TenderEvaluation.EvaluationStatus;
import com.xiyu.bid.tender.service.TenderEvaluationDocumentService;
import com.xiyu.bid.tender.service.TenderEvaluationReviewService;
import com.xiyu.bid.tender.service.TenderEvaluationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 2 TDD RED 测试：标讯项目评估新端点契约。
 * <p>覆盖 Phase 3 必须实现的三个端点：
 * <ul>
 *   <li>GET    /api/tenders/{tenderId}/evaluation         - 加载评估或初始化空草稿</li>
 *   <li>PUT    /api/tenders/{tenderId}/evaluation         - 保存草稿（无业务校验）</li>
 *   <li>POST   /api/tenders/{tenderId}/evaluation/submit  - 提交评估（强制校验）</li>
 * </ul>
 * <p>当前阶段：所有测试都 MUST 失败（404/405/方法不存在/状态不一致），
 * Phase 3 实现完成后转绿。
 */
class TenderEvaluationControllerTest {

    private TenderEvaluationService tenderEvaluationService;
    private TenderEvaluationDocumentService tenderEvaluationDocumentService;
    private TenderEvaluationReviewService tenderEvaluationReviewService;
    private TaskService taskService;
    private AuthService authService;
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @BeforeEach
    void setup() {
        tenderEvaluationService = mock(TenderEvaluationService.class);
        tenderEvaluationDocumentService = mock(TenderEvaluationDocumentService.class);
        tenderEvaluationReviewService = mock(TenderEvaluationReviewService.class);
        taskService = mock(TaskService.class);
        authService = mock(AuthService.class);
        when(authService.resolveUserIdByUsername("pm-1")).thenReturn(11L);
        when(authService.resolveUserIdByUsername("admin")).thenReturn(99L);
        when(authService.resolveUserIdByUsername("other")).thenReturn(22L);

        TenderEvaluationController controller =
                new TenderEvaluationController(tenderEvaluationService, tenderEvaluationDocumentService, tenderEvaluationReviewService, taskService, authService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        authenticateAs("pm-1", "MANAGER");
    }

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(String username, String role) {
        UserDetails principal = User.withUsername(username).password("x").roles(role).build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, "x", principal.getAuthorities()));
    }

    private TenderEvaluationDTO draftDto(Long tenderId) {
        return new TenderEvaluationDTO(
                tenderId, "测试标讯", Tender.Status.TRACKING,
                EvaluationStatus.DRAFT,
                null, null,
                11L, "pm-1", null,
                true, false,
                null, null, null,
                false, null, null, 1
        );
    }

    private TenderEvaluationDTO submittedDto(Long tenderId) {
        return new TenderEvaluationDTO(
                tenderId, "测试标讯", Tender.Status.EVALUATED,
                EvaluationStatus.SUBMITTED,
                BidRecommendation.RECOMMEND,
                LocalDateTime.of(2026, 5, 11, 10, 0),
                11L, "pm-1", LocalDateTime.of(2026, 5, 11, 10, 0),
                true, false,
                null, null, null,
                false, null, null, 1
        );
    }

    private TenderEvaluationSubmitRequest validRequest() {
        return new TenderEvaluationSubmitRequest(
                BidRecommendation.RECOMMEND,
                null, null, null
        );
    }

    // ---------- GET /api/tenders/{tenderId}/evaluation ----------

    @Test
    void get_returns200_withDraftDto_forProjectManager() throws Exception {
        // 新契约：即使没记录也返回 200 + 空草稿（Phase 3 行为）
        when(tenderEvaluationService.loadOrInitDraft(eq(1L), anyLong()))
                .thenReturn(draftDto(1L));

        mockMvc.perform(get("/api/tenders/1/evaluation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tenderId").value(1))
                .andExpect(jsonPath("$.data.evaluationStatus").value("DRAFT"));
    }

    @Test
    void get_returns404_whenTenderDoesNotExist() throws Exception {
        when(tenderEvaluationService.loadOrInitDraft(eq(404L), anyLong()))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "标讯不存在"));

        mockMvc.perform(get("/api/tenders/404/evaluation"))
                .andExpect(status().isNotFound());
    }

    @Test
    void get_returns403_whenCallerIsNeitherOwnerNorAdmin() throws Exception {
        authenticateAs("other", "STAFF");
        when(tenderEvaluationService.loadOrInitDraft(eq(1L), anyLong()))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "无权访问该标讯评估"));

        mockMvc.perform(get("/api/tenders/1/evaluation"))
                .andExpect(status().isForbidden());
    }

    // ---------- PUT /api/tenders/{tenderId}/evaluation ----------

    @Test
    void put_savesDraft_returns200_withDraftStatus() throws Exception {
        when(tenderEvaluationService.saveDraft(eq(1L), any(TenderEvaluationSubmitRequest.class), anyLong()))
                .thenReturn(draftDto(1L));

        mockMvc.perform(put("/api/tenders/1/evaluation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.evaluationStatus").value("DRAFT"));
    }

    @Test
    void put_returns403_whenCallerIsNotProjectManager() throws Exception {
        authenticateAs("other", "STAFF");
        when(tenderEvaluationService.saveDraft(eq(1L), any(), anyLong()))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "非项目经理不可编辑"));

        mockMvc.perform(put("/api/tenders/1/evaluation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isForbidden());
    }

    @Test
    void put_returns409_whenStatusAlreadySubmitted() throws Exception {
        when(tenderEvaluationService.saveDraft(eq(1L), any(), anyLong()))
                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "已提交，无法继续编辑草稿"));

        mockMvc.perform(put("/api/tenders/1/evaluation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isConflict());
    }

    @Test
    void put_returns400_forMalformedJson() throws Exception {
        mockMvc.perform(put("/api/tenders/1/evaluation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not valid json"))
                .andExpect(status().isBadRequest());
    }

    // ---------- POST /api/tenders/{tenderId}/evaluation/submit ----------

    @Test
    void postSubmit_returns200_withSubmittedStatus_whenValid() throws Exception {
        when(tenderEvaluationService.submit(eq(1L), any(TenderEvaluationSubmitRequest.class), anyLong()))
                .thenReturn(submittedDto(1L));

        mockMvc.perform(post("/api/tenders/1/evaluation/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.evaluationStatus").value("SUBMITTED"))
                .andExpect(jsonPath("$.data.submittedAt").exists());
    }

    @Test
    void postSubmit_returns400_whenProjectBackgroundMissing() throws Exception {
        when(tenderEvaluationService.submit(eq(1L), any(), anyLong()))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "projectBackground 不能为空"));

        TenderEvaluationSubmitRequest req = new TenderEvaluationSubmitRequest(
                BidRecommendation.RECOMMEND,
                null, null, null
        );
        mockMvc.perform(post("/api/tenders/1/evaluation/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void postSubmit_returns400_withFieldErrors_forMultipleMissingFields() throws Exception {
        when(tenderEvaluationService.submit(eq(1L), any(), anyLong()))
                .thenThrow(new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "projectBackground, competitorAnalysis, shortlistedCount 不能为空"));

        TenderEvaluationSubmitRequest req = new TenderEvaluationSubmitRequest(
                null,
                null, null, null
        );
        mockMvc.perform(post("/api/tenders/1/evaluation/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.msg").exists());
    }

    @Test
    void postSubmit_returns409_whenAlreadySubmitted() throws Exception {
        when(tenderEvaluationService.submit(eq(1L), any(), anyLong()))
                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "已提交，无法重复提交"));

        mockMvc.perform(post("/api/tenders/1/evaluation/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isConflict());
    }

    // ---------- H3: real-guard exception mapping ----------

    /**
     * H3 end-to-end check: when the service throws a real
     * {@link AccessDeniedException} (as the guard does for unauthorized users),
     * the controller + {@link GlobalExceptionHandler} pipeline must return 403,
     * not 500. This validates the guard's exception bubble path is wired right.
     */
    @Test
    void anyEvaluationEndpoint_returns403_whenServiceThrowsAccessDenied() throws Exception {
        // Re-wire mockMvc with GlobalExceptionHandler so AccessDeniedException
        // maps to 403 via the same advice the real stack uses.
        TenderEvaluationController controller =
                new TenderEvaluationController(tenderEvaluationService, tenderEvaluationDocumentService, tenderEvaluationReviewService, taskService, authService);
        MockMvc mvcWithAdvice = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        when(tenderEvaluationService.loadOrInitDraft(eq(1L), anyLong()))
                .thenThrow(new AccessDeniedException("forbidden: not your tender"));

        mvcWithAdvice.perform(get("/api/tenders/1/evaluation"))
                .andExpect(status().isForbidden());

        when(tenderEvaluationService.submit(eq(1L), any(), anyLong()))
                .thenThrow(new AccessDeniedException("forbidden: not your tender"));

        mvcWithAdvice.perform(post("/api/tenders/1/evaluation/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isForbidden());
    }
}
