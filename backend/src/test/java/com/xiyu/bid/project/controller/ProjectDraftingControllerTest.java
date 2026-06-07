// Input: 模拟 HTTP 请求 (PATCH leads / POST advance / GET)
// Output: 验证路由 + 状态码（200/409）
// Pos: backend test source - 单元级 MockMvc (standalone)
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.project.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.project.dto.ProjectDraftingViewDto;
import com.xiyu.bid.project.dto.ProjectLeadAssignmentRequest;
import com.xiyu.bid.project.service.ProjectDraftingService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProjectDraftingControllerTest {

    private ProjectDraftingService service;
    private AuthService authService;
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setup() {
        service = mock(ProjectDraftingService.class);
        authService = mock(AuthService.class);
        when(authService.resolveUserIdByUsername("admin")).thenReturn(42L);
        ProjectDraftingController controller = new ProjectDraftingController(service, authService);
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
    void patch_leads_ok() throws Exception {
        when(service.assignLeads(eq(1L), any(ProjectLeadAssignmentRequest.class), eq(42L)))
                .thenReturn(ProjectDraftingViewDto.builder()
                        .projectId(1L).primaryLeadUserId(10L).secondaryLeadUserId(20L)
                        .gateReady(true).incompleteTaskCount(0).build());
        mockMvc.perform(patch("/api/projects/1/drafting/leads")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                ProjectLeadAssignmentRequest.builder()
                                        .primaryLeadUserId(10L).secondaryLeadUserId(20L).build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.primaryLeadUserId").value(10));
    }

    @Test
    void post_advance_conflict_returns409() throws Exception {
        when(service.gateAdvanceToEvaluation(eq(1L), eq(42L)))
                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "仍有 2 个任务未完成"));
        mockMvc.perform(post("/api/projects/1/drafting/advance"))
                .andExpect(status().isConflict());
    }

    @Test
    void post_advance_happy() throws Exception {
        when(service.gateAdvanceToEvaluation(eq(1L), eq(42L)))
                .thenReturn(ProjectDraftingViewDto.builder()
                        .projectId(1L).gateReady(true).incompleteTaskCount(0).build());
        mockMvc.perform(post("/api/projects/1/drafting/advance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.gateReady").value(true));
    }

    @Test
    void get_returns_view() throws Exception {
        when(service.get(1L)).thenReturn(ProjectDraftingViewDto.builder()
                .projectId(1L).primaryLeadUserId(10L).gateReady(false).incompleteTaskCount(2).build());
        mockMvc.perform(get("/api/projects/1/drafting"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.incompleteTaskCount").value(2));
    }
}
