// Input: 模拟 HTTP 请求
// Output: 验证 POST /api/projects/{id}/transfer 路由 + 200/400/404 行为
// Pos: backend test source - 单元级 MockMvc (standalone)
// 维护声明: 覆盖 FR-001 转移入口；角色白名单由 @PreAuthorize 注解校验。
package com.xiyu.bid.project.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.xiyu.bid.exception.GlobalExceptionHandler;
import com.xiyu.bid.exception.ResourceNotFoundException;
import com.xiyu.bid.project.dto.ProjectTransferRequest;
import com.xiyu.bid.project.dto.ProjectTransferResponse;
import com.xiyu.bid.project.service.ProjectTransferService;
import com.xiyu.bid.service.AuthService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 项目转移控制器 MockMvc 单元测试。
 * <p>覆盖 FR-001：POST /api/projects/{projectId}/transfer 入口协议。
 * <ul>
 *   <li>200 成功：合法请求 / 带 reason</li>
 *   <li>400 参数校验：newOwnerUserId 为空/非正</li>
 *   <li>400 业务校验：新=旧 / 新负责人停用 / 角色不允许</li>
 *   <li>404 资源不存在：项目/新负责人</li>
 *   <li>注解校验：@PreAuthorize 角色白名单</li>
 * </ul>
 */
class ProjectTransferControllerTest {

    private ProjectTransferService service;
    private AuthService authService;
    private MockMvc mockMvc;
    private final ObjectMapper om = new ObjectMapper().registerModule(new JavaTimeModule());

    @BeforeEach
    void setup() {
        service = mock(ProjectTransferService.class);
        authService = mock(AuthService.class);
        when(authService.resolveUserIdByUsername("admin")).thenReturn(999L);
        ProjectTransferController controller = new ProjectTransferController(service, authService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        UserDetails principal = User.withUsername("admin").password("x").roles("ADMIN").build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, "x", principal.getAuthorities()));
    }

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    private ProjectTransferResponse sampleResponse() {
        return ProjectTransferResponse.builder()
                .projectId(135L).projectName("测试项目")
                .oldOwnerUserId(7246L).oldOwnerName("陈梦瑶")
                .newOwnerUserId(7324L).newOwnerName("周子靖")
                .transferredAt(LocalDateTime.now())
                .tenderSynced(true).tenderId(743L)
                .build();
    }

    // ── 200 OK ───────────────────────────────────────────────────────────

    @Test
    void transfer_success_returns200() throws Exception {
        when(service.transfer(eq(135L), eq(7324L), eq(999L), eq("误派修正")))
                .thenReturn(sampleResponse());

        mockMvc.perform(post("/api/projects/135/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(ProjectTransferRequest.builder()
                                .newOwnerUserId(7324L).reason("误派修正").build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.projectId").value(135))
                .andExpect(jsonPath("$.data.oldOwnerUserId").value(7246))
                .andExpect(jsonPath("$.data.oldOwnerName").value("陈梦瑶"))
                .andExpect(jsonPath("$.data.newOwnerUserId").value(7324))
                .andExpect(jsonPath("$.data.newOwnerName").value("周子靖"))
                .andExpect(jsonPath("$.data.tenderSynced").value(true))
                .andExpect(jsonPath("$.data.tenderId").value(743));
    }

    @Test
    void transfer_success_withoutReason_returns200() throws Exception {
        when(service.transfer(eq(135L), eq(7324L), eq(999L), eq(null)))
                .thenReturn(sampleResponse());

        mockMvc.perform(post("/api/projects/135/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(ProjectTransferRequest.builder()
                                .newOwnerUserId(7324L).build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.newOwnerUserId").value(7324));
    }

    // ── 400 Bad Request（参数校验） ──────────────────────────────────────

    @Test
    void transfer_missingNewOwnerUserId_returns400() throws Exception {
        mockMvc.perform(post("/api/projects/135/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(ProjectTransferRequest.builder()
                                .reason("误派修正").build())))  // 缺少 newOwnerUserId
                .andExpect(status().isBadRequest());
    }

    @Test
    void transfer_invalidNewOwnerUserId_zero_returns400() throws Exception {
        mockMvc.perform(post("/api/projects/135/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(ProjectTransferRequest.builder()
                                .newOwnerUserId(0L).build())))  // 非正整数
                .andExpect(status().isBadRequest());
    }

    @Test
    void transfer_invalidNewOwnerUserId_negative_returns400() throws Exception {
        mockMvc.perform(post("/api/projects/135/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(ProjectTransferRequest.builder()
                                .newOwnerUserId(-5L).build())))  // 负数
                .andExpect(status().isBadRequest());
    }

    // ── 400 Bad Request（业务校验 - IllegalArgumentException） ──────────

    @Test
    void transfer_newOwnerEqualsOldOwner_returns400() throws Exception {
        when(service.transfer(eq(135L), eq(7324L), eq(999L), eq(null)))
                .thenThrow(new IllegalArgumentException("新负责人与当前负责人相同，无需转移"));

        mockMvc.perform(post("/api/projects/135/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(ProjectTransferRequest.builder()
                                .newOwnerUserId(7324L).build())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void transfer_newOwnerDisabled_returns400() throws Exception {
        when(service.transfer(eq(135L), eq(7324L), eq(999L), eq(null)))
                .thenThrow(new IllegalArgumentException("新负责人账号已停用"));

        mockMvc.perform(post("/api/projects/135/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(ProjectTransferRequest.builder()
                                .newOwnerUserId(7324L).build())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void transfer_newOwnerRoleInvalid_returns400() throws Exception {
        when(service.transfer(eq(135L), eq(7324L), eq(999L), eq(null)))
                .thenThrow(new IllegalArgumentException("新负责人必须是投标项目负责人/组长/管理员"));

        mockMvc.perform(post("/api/projects/135/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(ProjectTransferRequest.builder()
                                .newOwnerUserId(7324L).build())))
                .andExpect(status().isBadRequest());
    }

    // ── 404 Not Found（ResourceNotFoundException） ───────────────────────

    @Test
    void transfer_projectNotFound_returns404() throws Exception {
        when(service.transfer(eq(999L), eq(7324L), eq(999L), eq(null)))
                .thenThrow(new ResourceNotFoundException("Project", "999"));

        mockMvc.perform(post("/api/projects/999/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(ProjectTransferRequest.builder()
                                .newOwnerUserId(7324L).build())))
                .andExpect(status().isNotFound());
    }

    @Test
    void transfer_newOwnerNotFound_returns404() throws Exception {
        when(service.transfer(eq(135L), eq(8888L), eq(999L), eq(null)))
                .thenThrow(new ResourceNotFoundException("User", "8888"));

        mockMvc.perform(post("/api/projects/135/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(ProjectTransferRequest.builder()
                                .newOwnerUserId(8888L).build())))
                .andExpect(status().isNotFound());
    }

    // ── @PreAuthorize 注解校验 ───────────────────────────────────────────

    /**
     * FR-003 / SC-002: 项目转移仅限投标管理员（/bidAdmin）与系统管理员（admin，对应 OSS
     * bid-SystemAdmin）操作。投标组长（bid-TeamLeader）不可操作。
     */
    @Test
    void transfer_preAuthorize_allows_only_admin_and_bidadmin() throws Exception {
        PreAuthorize annotation = ProjectTransferController.class
                .getMethod("transferProject", Long.class, ProjectTransferRequest.class, UserDetails.class)
                .getAnnotation(PreAuthorize.class);
        assertNotNull(annotation, "transferProject 端点必须保留 @PreAuthorize");
        String expr = annotation.value();
        assertTrue(expr.contains("ADMIN"),
                "transferProject @PreAuthorize 必须包含 ADMIN（含 bid-SystemAdmin 映射），当前: " + expr);
        assertTrue(expr.contains("BIDADMIN"),
                "transferProject @PreAuthorize 必须包含 BIDADMIN（投标管理员），当前: " + expr);
        assertFalse(expr.contains("BID_TEAMLEADER"),
                "transferProject @PreAuthorize 不得包含 BID_TEAMLEADER——投标组长不可操作项目转移，当前: " + expr);
    }
}
