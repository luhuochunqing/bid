// Input: 模拟 HTTP 请求
// Output: 验证 PATCH/POST/GET 路由 + 校验 + 服务层调用
// Pos: backend test source - 单元级 MockMvc (standalone)
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.project.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.project.core.EvaluationSubStage;
import com.xiyu.bid.project.dto.EvaluationDTO;
import com.xiyu.bid.project.dto.EvaluationEvidenceAttachRequest;
import com.xiyu.bid.project.dto.EvaluationSubStageUpdateRequest;
import com.xiyu.bid.project.service.ProjectEvaluationService;
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

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProjectEvaluationControllerTest {

    private ProjectEvaluationService service;
    private AuthService authService;
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setup() {
        service = mock(ProjectEvaluationService.class);
        authService = mock(AuthService.class);
        when(authService.resolveUserIdByUsername("manager")).thenReturn(7L);
        ProjectEvaluationController controller = new ProjectEvaluationController(service, authService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
        UserDetails principal = User.withUsername("manager").password("x").roles("MANAGER").build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, "x", principal.getAuthorities()));
    }

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void patch_subStage_happy() throws Exception {
        EvaluationDTO dto = EvaluationDTO.builder()
                .id(10L).projectId(1L).subStage("AWAITING_BOARD").build();
        when(service.transitionSubStage(eq(1L), any(), any())).thenReturn(dto);
        var req = EvaluationSubStageUpdateRequest.builder()
                .targetSubStage(EvaluationSubStage.AWAITING_BOARD).notes("评标情况说明").build();
        mockMvc.perform(patch("/api/projects/1/evaluation/sub-stage")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.subStage").value("AWAITING_BOARD"));
    }

    @Test
    void patch_subStage_serviceConflict_propagates409() throws Exception {
        // 当 service 抛 409 (如同态/锁定阶段) 时 Controller 透传
        when(service.transitionSubStage(eq(1L), any(), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "不能切换到当前子状态"));
        var req = EvaluationSubStageUpdateRequest.builder()
                .targetSubStage(EvaluationSubStage.ANNOUNCED).notes("评标情况说明").build();
        mockMvc.perform(patch("/api/projects/1/evaluation/sub-stage")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict());
    }

    @Test
    void post_evidence_happy() throws Exception {
        EvaluationDTO dto = EvaluationDTO.builder()
                .id(10L).projectId(1L).subStage("IN_PROGRESS")
                .evidenceDocIds(List.of(50L)).build();
        when(service.attachEvidence(eq(1L), any(), any())).thenReturn(dto);
        var req = EvaluationEvidenceAttachRequest.builder().fileIds(List.of(50L)).build();
        mockMvc.perform(post("/api/projects/1/evaluation/evidence")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.evidenceDocIds[0]").value(50));
    }

    @Test
    void get_returns_dto() throws Exception {
        EvaluationDTO dto = EvaluationDTO.builder()
                .id(10L).projectId(1L).subStage("IN_PROGRESS").build();
        when(service.getByProject(1L)).thenReturn(Optional.of(dto));
        mockMvc.perform(get("/api/projects/1/evaluation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.subStage").value("IN_PROGRESS"));
    }

    @Test
    void get_notFound_returns404() throws Exception {
        when(service.getByProject(1L)).thenReturn(Optional.empty());
        mockMvc.perform(get("/api/projects/1/evaluation"))
                .andExpect(status().isNotFound());
    }
}
