// Input: 模拟 HTTP 请求
// Output: 验证 POST/PATCH/GET 路由 + 422/423/404 行为
// Pos: backend test source - 单元级 MockMvc (standalone)
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.project.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.xiyu.bid.project.core.InitiationFieldPolicy;
import com.xiyu.bid.project.dto.InitiationDto;
import com.xiyu.bid.project.dto.InitiationViewDto;
import com.xiyu.bid.project.service.ProjectInitiationService;
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
import java.time.LocalDateTime;
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

class ProjectInitiationControllerTest {

    private ProjectInitiationService service;
    private com.xiyu.bid.project.service.ProjectInitiationApprovalService approvalService;
    private AuthService authService;
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @BeforeEach
    void setup() {
        service = mock(ProjectInitiationService.class);
        approvalService = mock(com.xiyu.bid.project.service.ProjectInitiationApprovalService.class);
        authService = mock(AuthService.class);
        when(authService.resolveUserIdByUsername("sales")).thenReturn(42L);
        ProjectInitiationController controller = new ProjectInitiationController(service, approvalService, authService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
        UserDetails principal = User.withUsername("sales").password("x").roles("STAFF").build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, "x", principal.getAuthorities()));
    }

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    private InitiationDto fullDto() {
        return InitiationDto.builder()
                .ownerUnit("国网")
                .expectedBidders(3).contractPeriodMonths(12)
                .projectType(InitiationFieldPolicy.ProjectType.OFFICE)
                .customerType(InitiationFieldPolicy.CustomerType.CENTRAL_SOE)
                .annualRevenue(new BigDecimal("100000"))
                .bidOpenTime(LocalDateTime.of(2026, 6, 1, 9, 30))
                .ownerUserId(42L).departmentSnapshot("投标部")
                .depositAmount(new BigDecimal("50000")).depositPaymentMethod("银行汇票")
                .build();
    }

    @Test
    void post_submit_happy() throws Exception {
        InitiationViewDto view = InitiationViewDto.builder()
                .id(10L).projectId(1L).ownerUnit("国网").bidMonth("2026-06").locked(true).build();
        when(service.submit(eq(1L), any(InitiationDto.class), eq(42L))).thenReturn(view);
        mockMvc.perform(post("/api/projects/1/initiation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(fullDto())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.locked").value(true))
                .andExpect(jsonPath("$.data.bidMonth").value("2026-06"));
    }

    @Test
    void post_submit_missingFields_returns422() throws Exception {
        when(service.submit(eq(1L), any(InitiationDto.class), eq(42L)))
                .thenThrow(new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "缺少必填字段：ownerUnit"));
        mockMvc.perform(post("/api/projects/1/initiation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(fullDto())))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void patch_update_lockedField_returns423() throws Exception {
        when(service.update(eq(1L), any(InitiationDto.class), eq(42L)))
                .thenThrow(new ResponseStatusException(HttpStatus.LOCKED, "提交后不可修改：bidOpenTime"));
        mockMvc.perform(patch("/api/projects/1/initiation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                InitiationDto.builder().bidOpenTime(LocalDateTime.now()).build())))
                .andExpect(status().isLocked());
    }

    @Test
    void patch_update_happy() throws Exception {
        InitiationViewDto view = InitiationViewDto.builder()
                .id(10L).projectId(1L).depositAmount(new BigDecimal("99999")).locked(true).build();
        when(service.update(eq(1L), any(InitiationDto.class), eq(42L))).thenReturn(view);
        mockMvc.perform(patch("/api/projects/1/initiation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                InitiationDto.builder().depositAmount(new BigDecimal("99999")).build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.depositAmount").value(99999));
    }

    @Test
    void get_returns_dto() throws Exception {
        InitiationViewDto view = InitiationViewDto.builder()
                .id(10L).projectId(1L).ownerUnit("国网").locked(true).build();
        when(service.getByProject(1L)).thenReturn(Optional.of(view));
        mockMvc.perform(get("/api/projects/1/initiation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ownerUnit").value("国网"));
    }

    @Test
    void get_notFound_returns404() throws Exception {
        when(service.getByProject(1L)).thenReturn(Optional.empty());
        mockMvc.perform(get("/api/projects/1/initiation"))
                .andExpect(status().isNotFound());
    }
}
