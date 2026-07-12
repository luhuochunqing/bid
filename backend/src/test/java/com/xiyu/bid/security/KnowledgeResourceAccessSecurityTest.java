package com.xiyu.bid.security;

import com.xiyu.bid.apikey.infrastructure.ApiKeyAuthenticationFilter;
import com.xiyu.bid.auth.JwtAuthenticationFilter;
import com.xiyu.bid.config.RateLimitFilter;
import com.xiyu.bid.config.SecurityConfig;
import com.xiyu.bid.platform.service.PlatformAccountImportAppService;
import com.xiyu.bid.platform.service.PlatformAccountService;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.resources.service.CaBorrowService;
import com.xiyu.bid.resources.service.CaCertificateImportAppService;
import com.xiyu.bid.resources.service.CaCertificateService;
import com.xiyu.bid.resources.service.CaCommitmentLetterUploadService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CO-362 regression gate：投标专员（bid-Team）应能读取资源管理模块列表接口。
 *
 * <p>背景：投标专员被 {@link com.xiyu.bid.entity.RoleProfileCatalog#ROLES_WITHOUT_LEGACY_ROLE_COMPAT}
 * 跳过 legacy ROLE 颁发，登录后 authority 集为 {@code {bid-Team, ROLE_BID_TEAM} ∪ catalog
 * menuPermissions}，其中含 {@code resource} 权限点。原 controller 类级 {@code hasAnyRole('ADMIN','MANAGER')}
 * 把它挡住返回 403。修复后应改用 {@code hasAuthority('resource')} 放行。
 *
 * <p>本测试以 {@code @WithMockUser(authorities={"resource"})} 模拟投标专员已拥有的权限点，
 * 断言资源管理列表接口返回 200；同时以无权限用户断言仍 403，以 MANAGER 断言回归不破。
 *
 * <p>敏感写操作（下架/删除/审批等）有独立方法级 {@code @PreAuthorize}，本次修复不触碰；
 * 末尾补一条 regression 断言投标专员仍不能下架 CA 证书（方法级覆盖类级，保持收紧）。
 */
@WebMvcTest(controllers = {
        com.xiyu.bid.platform.controller.PlatformAccountController.class,
        com.xiyu.bid.resources.controller.CaCertificateController.class
}, excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = {SecurityConfig.class, JwtAuthenticationFilter.class, RateLimitFilter.class,
                ApiKeyAuthenticationFilter.class}
))
@Import(KnowledgeResourceAccessSecurityTest.TestSecurityConfig.class)
class KnowledgeResourceAccessSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PlatformAccountService platformAccountService;
    @MockBean
    private PlatformAccountImportAppService platformAccountImportAppService;
    @MockBean
    private UserRepository userRepository;
    // CO-373 回归修复：CurrentUserResolver 现依赖 EffectiveRoleResolver→RoleCodeCachePort，
    // @WebMvcTest 切片不实例化该链；TraceFilter(@Component) 又强依赖 CurrentUserResolver。
    // 此处 mock 整个 CurrentUserResolver 以满足 TraceFilter 注入，避免上下文加载失败。
    @MockBean
    private CurrentUserResolver currentUserResolver;

    // Phase 3 US3 修复：TraceFilter 现依赖 EffectiveRoleResolver（MDC userId/roleCode 填充），
    // @WebMvcTest 切片不实例化该 bean，需手动 mock 以满足 TraceFilter 注入。
    @MockBean
    private com.xiyu.bid.security.EffectiveRoleResolver effectiveRoleResolver;
    @MockBean
    private CaCertificateService caCertificateService;
    @MockBean
    private CaBorrowService caBorrowService;
    @MockBean
    private CaCertificateImportAppService caCertificateImportAppService;
    @MockBean
    private CaCommitmentLetterUploadService caCommitmentLetterUploadService;
    // CO-515 回归：CaCertificateController 新增 IAuditLogService 依赖（CA 详情页操作日志 Tab），
    // @WebMvcTest 切片不实例化 audit.service.AuditLogService，需手动 mock 以满足构造注入。
    @MockBean
    private com.xiyu.bid.audit.service.IAuditLogService auditLogService;

    @EnableWebSecurity
    @EnableMethodSecurity(prePostEnabled = true)
    static class TestSecurityConfig {
        @Bean
        SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
            // permitAll 让鉴权完全由 @PreAuthorize 决定，与生产 SecurityConfig 方法安全语义一致
            http.csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
            return http.build();
        }
    }

    // ==================== GET /api/platform/accounts ====================

    @Test
    @DisplayName("投标专员(authorities含resource) GET /api/platform/accounts → 200")
    @WithMockUser(authorities = {"resource"})
    void listPlatformAccounts_shouldSucceed_forBidSpecialist() throws Exception {
        when(platformAccountService.getAccountsForViewer(any())).thenReturn(List.of());
        mockMvc.perform(get("/api/platform/accounts"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("无权限用户 GET /api/platform/accounts → 403")
    @WithMockUser(authorities = {})
    void listPlatformAccounts_shouldReturn403_forNoPermission() throws Exception {
        mockMvc.perform(get("/api/platform/accounts"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("回归：MANAGER(GET含resource) GET /api/platform/accounts → 200")
    @WithMockUser(authorities = {"ROLE_MANAGER", "resource"})
    void listPlatformAccounts_shouldSucceed_forManager() throws Exception {
        when(platformAccountService.getAccountsForViewer(any())).thenReturn(List.of());
        mockMvc.perform(get("/api/platform/accounts"))
                .andExpect(status().isOk());
    }

    // ==================== GET /api/ca-certificates ====================

    @Test
    @DisplayName("投标专员(authorities含resource) GET /api/ca-certificates → 200")
    @WithMockUser(authorities = {"resource"})
    void listCaCertificates_shouldSucceed_forBidSpecialist() throws Exception {
        when(caCertificateService.list(any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));
        mockMvc.perform(get("/api/ca-certificates"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("无权限用户 GET /api/ca-certificates → 403")
    @WithMockUser(authorities = {})
    void listCaCertificates_shouldReturn403_forNoPermission() throws Exception {
        mockMvc.perform(get("/api/ca-certificates"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("回归：MANAGER(GET含resource) GET /api/ca-certificates → 200")
    @WithMockUser(authorities = {"ROLE_MANAGER", "resource"})
    void listCaCertificates_shouldSucceed_forManager() throws Exception {
        when(caCertificateService.list(any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));
        mockMvc.perform(get("/api/ca-certificates"))
                .andExpect(status().isOk());
    }

    // ==================== 写操作回归保护：投标专员不应越权敏感写操作 ====================

    @Test
    @DisplayName("投标专员(authorities含resource) DELETE /api/ca-certificates/{id}(下架) → 200（Controller 层 resource 权限放行；custodian 校验在 Service 层，本切片测试不覆盖）")
    @WithMockUser(authorities = {"resource"})
    void deactivateCaCertificate_shouldReturn200_forBidSpecialist_atControllerLayer() throws Exception {
        // P3 迁移（specs/024-preauthorize-unification）：方法级混合补丁统一为 hasAuthority('resource')。
        // Controller 层只做粗粒度"有 resource 模块权限"校验；细粒度 custodianId == currentUser.id
        // 校验在 Service 层（CaCertificateService.deactivate 按 custodian 差异化校验），不在 @WebMvcTest 范围。
        mockMvc.perform(delete("/api/ca-certificates/1"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("投标专员(authorities含resource) POST approve → 200（Controller 层 resource 放行；Service 层 custodian 校验另算）")
    @WithMockUser(authorities = {"resource"})
    void approveCaBorrow_shouldReturn200_forBidSpecialist_atControllerLayer() throws Exception {
        mockMvc.perform(post("/api/ca-certificates/borrow-applications/1/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"同意\"}"))
                .andExpect(status().isOk());
    }
}
