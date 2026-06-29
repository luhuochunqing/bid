// Input: 模拟 HTTP 请求
// Output: 验证 GET preview / POST submit 路由 + 409/423/422 行为
// Pos: backend test source - 单元级 MockMvc (standalone)
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.project.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.xiyu.bid.project.dto.ClosureDTO;
import com.xiyu.bid.project.dto.ClosurePreviewDTO;
import com.xiyu.bid.project.dto.ClosureSubmitRequest;
import com.xiyu.bid.project.service.ProjectClosureService;
import com.xiyu.bid.service.AuthService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProjectClosureControllerTest {

    private ProjectClosureService service;
    private AuthService authService;
    private MockMvc mockMvc;
    private final ObjectMapper om = new ObjectMapper().registerModule(new JavaTimeModule());

    @BeforeEach
    void setup() {
        service = mock(ProjectClosureService.class);
        authService = mock(AuthService.class);
        when(authService.resolveUserIdByUsername("admin")).thenReturn(1L);
        ProjectClosureController controller = new ProjectClosureController(service, authService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
        UserDetails principal = User.withUsername("admin").password("x").roles("ADMIN").build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, "x", principal.getAuthorities()));
    }

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void preview_returns_dto() throws Exception {
        when(service.preview(1L)).thenReturn(ClosurePreviewDTO.builder()
                .projectId(1L).hasDeposit(true).depositReturnStatus("NOT_RETURNED")
                .canClose(false).blockingReasons(List.of("保证金未退回")).build());
        mockMvc.perform(get("/api/projects/1/closure/preview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.canClose").value(false))
                .andExpect(jsonPath("$.data.blockingReasons[0]").value("保证金未退回"));
    }

    @Test
    void submit_happy_returns201() throws Exception {
        when(service.submitClosure(eq(1L), any(ClosureSubmitRequest.class), eq(1L)))
                .thenReturn(ClosureDTO.builder().id(10L).projectId(1L).stageLocked(true)
                        .closedAt(LocalDateTime.now()).build());
        mockMvc.perform(post("/api/projects/1/closure")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(ClosureSubmitRequest.builder().build())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.stageLocked").value(true));
    }

    @Test
    void submit_depositNotReturned_returns409() throws Exception {
        when(service.submitClosure(eq(1L), any(ClosureSubmitRequest.class), eq(1L)))
                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "保证金未退回"));
        mockMvc.perform(post("/api/projects/1/closure")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(ClosureSubmitRequest.builder().build())))
                .andExpect(status().isConflict());
    }

    @Test
    void submit_alreadyClosed_returns423() throws Exception {
        when(service.submitClosure(eq(1L), any(ClosureSubmitRequest.class), eq(1L)))
                .thenThrow(new ResponseStatusException(HttpStatus.LOCKED, "项目已结项，不可重复操作"));
        mockMvc.perform(post("/api/projects/1/closure")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(ClosureSubmitRequest.builder().build())))
                .andExpect(status().isLocked());
    }

    @Test
    void submit_fullyReturnedWithoutEvidence_returns422() throws Exception {
        // depositReturnStatus=FULLY_RETURNED 但未提供 date/evidence → controller 层早期拒绝 422
        ClosureSubmitRequest req = ClosureSubmitRequest.builder().depositReturnStatus("FULLY_RETURNED").build();
        mockMvc.perform(post("/api/projects/1/closure")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(req)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void approve_happy_returns200() throws Exception {
        when(service.approveClosure(eq(1L), eq(1L)))
                .thenReturn(ClosureDTO.builder().id(10L).projectId(1L).stageLocked(true)
                        .closedAt(LocalDateTime.now()).build());
        mockMvc.perform(post("/api/projects/1/closure/approve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stageLocked").value(true));
    }

    @Test
    void exportDocuments_happy_returns200() throws Exception {
        when(service.exportDocuments(eq(1L), eq(1L)))
                .thenReturn(com.xiyu.bid.documentexport.dto.DocumentExportDTO.builder()
                        .id(999L).projectId(1L).projectName("测试项目").format("json").build());
        mockMvc.perform(post("/api/projects/1/closure/export-documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.format").value("json"));
    }

    /** CO-392: 结项预览端点 @PreAuthorize 必须对齐同 Controller 其他结项端点的角色白名单，
     *  允许投标辅助(bid-Team)/投标负责人(BID_PROJECTLEADER)/投标组长(BID_TEAMLEADER)/投标管理员(BIDADMIN)。
     *  bid-Team 在 ROLES_WITHOUT_LEGACY_ROLE_COMPAT 中，不发 ROLE_MANAGER，故仅靠 'ADMIN','MANAGER' 会被 403 拦截，
     *  导致前端 preview.value 为 null、整页内容空白（与投标管理员/组长显示不一致）。 */
    @Test
    void co392_preview_preauthorize_allows_bid_team_and_project_leader() throws Exception {
        PreAuthorize preview = ProjectClosureController.class
                .getMethod("preview", Long.class)
                .getAnnotation(PreAuthorize.class);
        assertNotNull(preview, "preview 端点必须保留 @PreAuthorize");
        String expr = preview.value();
        assertTrue(expr.contains("BID_TEAM"),
                "preview @PreAuthorize 必须包含 BID_TEAM（投标辅助），当前: " + expr);
        assertTrue(expr.contains("BID_PROJECTLEADER"),
                "preview @PreAuthorize 必须包含 BID_PROJECTLEADER（投标负责人），当前: " + expr);
        assertTrue(expr.contains("BID_TEAMLEADER"),
                "preview @PreAuthorize 必须包含 BID_TEAMLEADER（投标组长），当前: " + expr);
        assertTrue(expr.contains("BIDADMIN"),
                "preview @PreAuthorize 必须包含 BIDADMIN（投标管理员），当前: " + expr);
    }
}
