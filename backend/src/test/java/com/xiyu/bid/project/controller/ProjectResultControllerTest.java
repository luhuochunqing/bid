// Input: 模拟 HTTP 请求
// Output: 验证 POST/GET 路由 + 422/409/404 行为
// Pos: backend test source - 单元级 MockMvc (standalone)
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.project.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.xiyu.bid.project.core.BidResultType;
import com.xiyu.bid.project.dto.ResultDTO;
import com.xiyu.bid.project.dto.ResultRegistrationRequest;
import com.xiyu.bid.project.service.ProjectResultRegistrationService;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProjectResultControllerTest {

    private ProjectResultRegistrationService service;
    private AuthService authService;
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @BeforeEach
    void setup() {
        service = mock(ProjectResultRegistrationService.class);
        authService = mock(AuthService.class);
        when(authService.resolveUserIdByUsername("manager")).thenReturn(42L);
        ProjectResultController controller = new ProjectResultController(service, authService);
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

    private ResultRegistrationRequest wonReq() {
        return ResultRegistrationRequest.builder()
                .resultType(BidResultType.WON)
                .awardAmount(new BigDecimal("100000"))
                .evidenceFileIds(List.of(101L))
                .summary("中标")
                .build();
    }

    @Test
    void post_register_happy_returns201() throws Exception {
        ResultDTO dto = ResultDTO.builder()
                .id(10L).projectId(1L).resultType("WON")
                .awardAmount(new BigDecimal("100000"))
                .evidenceFileIds(List.of(101L))
                .summary("中标").build();
        when(service.register(eq(1L), any(ResultRegistrationRequest.class), eq(42L))).thenReturn(dto);
        mockMvc.perform(post("/api/projects/1/result")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(wonReq())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.resultType").value("WON"))
                .andExpect(jsonPath("$.data.evidenceFileIds[0]").value(101));
    }

    @Test
    void post_register_missingFields_returns422() throws Exception {
        when(service.register(eq(1L), any(ResultRegistrationRequest.class), eq(42L)))
                .thenThrow(new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "缺少必填字段：evidenceFileIds"));
        mockMvc.perform(post("/api/projects/1/result")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(wonReq())))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void post_register_alreadyRegistered_returns409() throws Exception {
        when(service.register(eq(1L), any(ResultRegistrationRequest.class), eq(42L)))
                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "项目结果已登记"));
        mockMvc.perform(post("/api/projects/1/result")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(wonReq())))
                .andExpect(status().isConflict());
    }

    @Test
    void get_returns_dto() throws Exception {
        ResultDTO dto = ResultDTO.builder()
                .id(10L).projectId(1L).resultType("LOST")
                .evidenceFileIds(List.of(202L)).build();
        when(service.getByProject(1L)).thenReturn(Optional.of(dto));
        mockMvc.perform(get("/api/projects/1/result"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resultType").value("LOST"));
    }

    @Test
    void get_notFound_returnsNullData() throws Exception {
        when(service.getByProject(1L)).thenReturn(Optional.empty());
        mockMvc.perform(get("/api/projects/1/result"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
    }
}
