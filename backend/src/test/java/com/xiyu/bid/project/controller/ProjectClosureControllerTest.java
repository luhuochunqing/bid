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
}
