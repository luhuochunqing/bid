package com.xiyu.bid.platform.controller;

import com.xiyu.bid.apikey.infrastructure.ApiKeyAuthenticationFilter;
import com.xiyu.bid.auth.JwtAuthenticationFilter;
import com.xiyu.bid.config.RateLimitFilter;
import com.xiyu.bid.config.SecurityConfig;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.platform.dto.PlatformAccountDTO;
import com.xiyu.bid.platform.service.PlatformAccountImportAppService;
import com.xiyu.bid.platform.service.PlatformAccountService;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.security.CurrentUserResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CO-400 round5: 平台账户密码查看接口的权限边界回归门禁。
 *
 * <p>验证 {@code GET /api/platform/accounts/{id}/password} 的权限策略：
 * Controller 类级 {@code @PreAuthorize("hasAuthority('resource')")} 统一边界，
 * 所有带 resource 权限的已登录用户通过 Controller 层；真权限（管理员 OR
 * 账户绑定联系人 custodian）交给 Service 层 Policy。无 resource 权限的
 * 用户被类级 @PreAuthorize 拦截返回 403。
 *
 * <p>本测试只覆盖 controller 层 {@code @PreAuthorize} 行为，
 * 不验证 service 层角色校验（由 {@code PlatformAccountServiceTest} 覆盖），
 * 也不验证密码解密正确性（由 service 单元测试覆盖）。
 */
@WebMvcTest(controllers = PlatformAccountController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {SecurityConfig.class, JwtAuthenticationFilter.class, RateLimitFilter.class,
                        ApiKeyAuthenticationFilter.class}
        ))
@Import(PlatformAccountControllerSecurityTest.TestSecurityConfig.class)
@AutoConfigureMockMvc(addFilters = false)
class PlatformAccountControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PlatformAccountService platformAccountService;

    @MockBean
    private PlatformAccountImportAppService importAppService;

    @MockBean
    private UserRepository userRepository;

    // CO-373 回归修复：CurrentUserResolver 现依赖 EffectiveRoleResolver→RoleCodeCachePort，
    // @WebMvcTest 切片不实例化该链；TraceFilter(@Component) 又强依赖 CurrentUserResolver。
    // 此处 mock 整个 CurrentUserResolver 以满足 TraceFilter 注入，避免上下文加载失败。
    @MockBean
    private CurrentUserResolver currentUserResolver;

    @EnableWebSecurity
    @EnableMethodSecurity(prePostEnabled = true)
    static class TestSecurityConfig {
        @Bean
        SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
            // permitAll 让鉴权完全由 @PreAuthorize（方法级）决定，模拟生产 SecurityConfig 的方法安全语义
            http.csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
            return http.build();
        }
    }

    // ── 通过路径（CO-389 放开） ──────────────────────────────────────────────

    @Test
    @DisplayName("admin 可以查看密码")
    @WithMockUser(authorities = {"admin", "ROLE_ADMIN", "resource"})
    void admin_GET_password_shouldReturn200() throws Exception {
        when(userRepository.findByUsername(any())).thenReturn(Optional.of(
                User.builder().id(1L).username("admin").role(User.Role.ADMIN).build()));
        when(platformAccountService.getPassword(eq(1L), any()))
                .thenReturn("secret123");

        mockMvc.perform(get("/api/platform/accounts/1/password"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("bidAdmin 可以查看密码（CO-389 新放开）")
    @WithMockUser(authorities = {"/bidAdmin", "ROLE_BIDADMIN", "resource"})
    void bidAdmin_GET_password_shouldReturn200() throws Exception {
        when(userRepository.findByUsername(any())).thenReturn(Optional.of(
                User.builder().id(2L).username("bid_admin").role(User.Role.MANAGER).build()));
        when(platformAccountService.getPassword(eq(1L), any()))
                .thenReturn("secret123");

        mockMvc.perform(get("/api/platform/accounts/1/password"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("bid-TeamLeader 可以查看密码（CO-389 新放开）")
    @WithMockUser(authorities = {"bid-TeamLeader", "ROLE_BID_TEAMLEADER", "resource"})
    void bid_leader_GET_password_shouldReturn200() throws Exception {
        when(userRepository.findByUsername(any())).thenReturn(Optional.of(
                User.builder().id(3L).username("bid_lead").role(User.Role.MANAGER).build()));
        when(platformAccountService.getPassword(eq(1L), any()))
                .thenReturn("secret123");

        mockMvc.perform(get("/api/platform/accounts/1/password"))
                .andExpect(status().isOk());
    }

    // ── CO-400 round5 review: 删除方法级 @PreAuthorize 覆盖，让类级
    // hasAuthority('resource') 生效。所有带 resource 权限的已登录用户通过
    // Controller 层，真权限（管理员 OR 账户绑定联系人 custodian）由 Service
    // 层 Policy 决定。无 resource 权限的用户被类级 @PreAuthorize 拦截（403）。

    @Test
    @DisplayName("bid-Team（带 resource 权限）通过 Controller 层（Service 层决定是否放行）")
    @WithMockUser(authorities = {"bid-Team", "ROLE_BID_TEAM", "resource"})
    void bidTeam_GET_password_shouldPassControllerLayer() throws Exception {
        when(userRepository.findByUsername(any())).thenReturn(Optional.of(
                User.builder().id(5L).username("bid_specialist").role(User.Role.MANAGER).build()));
        when(platformAccountService.getPassword(eq(1L), any()))
                .thenReturn("secret123");

        mockMvc.perform(get("/api/platform/accounts/1/password"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("sales（bid-projectLeader，带 resource 权限）通过 Controller 层（Service 层决定是否放行）")
    @WithMockUser(authorities = {"bid-projectLeader", "ROLE_BID_PROJECTLEADER", "resource"})
    void sales_GET_password_shouldPassControllerLayer() throws Exception {
        when(userRepository.findByUsername(any())).thenReturn(Optional.of(
                User.builder().id(6L).username("sales").role(User.Role.MANAGER).build()));
        when(platformAccountService.getPassword(eq(1L), any()))
                .thenReturn("secret123");

        mockMvc.perform(get("/api/platform/accounts/1/password"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("已登录但无 resource 权限的用户被类级 @PreAuthorize 拦截（403）")
    @WithMockUser(authorities = {"bid-Team", "ROLE_BID_TEAM"})
    void noResourceAuthority_GET_password_shouldReturn403() throws Exception {
        mockMvc.perform(get("/api/platform/accounts/1/password"))
                .andExpect(status().isForbidden());
    }

    // ── CO-415: return 端点 Controller 层权限边界（对称于 password 端点） ──
    // Controller 类级 @PreAuthorize("hasAuthority('resource')") 是唯一防线；
    // 带 resource 权限的已登录用户通过 Controller 层，真权限（管理员 OR 账户
    // 绑定联系人）交给 Service 层 assertCanReturnAccount 决定。

    @Test
    @DisplayName("CO-415：admin 可以通过 Controller 层调用 return（Service 层放行）")
    @WithMockUser(authorities = {"admin", "ROLE_ADMIN", "resource"})
    void admin_POST_return_shouldReturn200() throws Exception {
        when(userRepository.findByUsername(any())).thenReturn(Optional.of(
                User.builder().id(1L).username("admin").role(User.Role.ADMIN).build()));
        when(platformAccountService.returnAccount(eq(1L), any()))
                .thenReturn(PlatformAccountDTO.builder().id(1L).build());

        mockMvc.perform(post("/api/platform/accounts/1/return"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("CO-415：bid-Team（带 resource 权限）通过 Controller 层（Service 层决定是否放行）")
    @WithMockUser(authorities = {"bid-Team", "ROLE_BID_TEAM", "resource"})
    void bidTeam_POST_return_shouldPassControllerLayer() throws Exception {
        when(userRepository.findByUsername(any())).thenReturn(Optional.of(
                User.builder().id(5L).username("bid_specialist").role(User.Role.MANAGER).build()));
        // Service 层会做联系人校验；此处 mock 放行，只验证 Controller 层不拦截 bid-Team
        when(platformAccountService.returnAccount(eq(1L), any()))
                .thenReturn(PlatformAccountDTO.builder().id(1L).build());

        mockMvc.perform(post("/api/platform/accounts/1/return"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("CO-415：已登录但无 resource 权限的用户调用 return 被类级 @PreAuthorize 拦截（403）")
    @WithMockUser(authorities = {"bid-Team", "ROLE_BID_TEAM"})
    void noResourceAuthority_POST_return_shouldReturn403() throws Exception {
        mockMvc.perform(post("/api/platform/accounts/1/return"))
                .andExpect(status().isForbidden());
    }

    // ── CO-416: update 端点 Controller 层权限边界（对称于 password/return 端点） ──
    // 移除方法级 @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")，让类级
    // @PreAuthorize("hasAuthority('resource')") 生效。投标专员带 resource 权限
    // 通过 Controller 层，真权限（管理员 OR 账户绑定联系人）交给 Service 层
    // PlatformAccountViewerPolicy.checkCanManageAccount 决定。

    @Test
    @DisplayName("CO-416：admin 可以通过 Controller 层调用 update（Service 层放行）")
    @WithMockUser(authorities = {"admin", "ROLE_ADMIN", "resource"})
    void admin_PUT_update_shouldReturn200() throws Exception {
        when(userRepository.findByUsername(any())).thenReturn(Optional.of(
                User.builder().id(1L).username("admin").role(User.Role.ADMIN).build()));
        when(platformAccountService.updateAccount(eq(1L), any(), any()))
                .thenReturn(PlatformAccountDTO.builder().id(1L).build());

        mockMvc.perform(put("/api/platform/accounts/1")
                        .contentType("application/json")
                        .content("{\"username\":\"u\",\"password\":\"p\",\"accountName\":\"n\",\"platformType\":\"GOV_PROCUREMENT\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("CO-416：bid-Team（带 resource 权限）通过 Controller 层（Service 层决定是否放行）")
    @WithMockUser(authorities = {"bid-Team", "ROLE_BID_TEAM", "resource"})
    void bidTeam_PUT_update_shouldPassControllerLayer() throws Exception {
        when(userRepository.findByUsername(any())).thenReturn(Optional.of(
                User.builder().id(5L).username("bid_specialist").role(User.Role.MANAGER).build()));
        // Service 层会做联系人校验；此处 mock 放行，只验证 Controller 层不拦截 bid-Team
        when(platformAccountService.updateAccount(eq(1L), any(), any()))
                .thenReturn(PlatformAccountDTO.builder().id(1L).build());

        mockMvc.perform(put("/api/platform/accounts/1")
                        .contentType("application/json")
                        .content("{\"username\":\"u\",\"password\":\"p\",\"accountName\":\"n\",\"platformType\":\"GOV_PROCUREMENT\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("CO-416：已登录但无 resource 权限的用户调用 update 被类级 @PreAuthorize 拦截（403）")
    @WithMockUser(authorities = {"bid-Team", "ROLE_BID_TEAM"})
    void noResourceAuthority_PUT_update_shouldReturn403() throws Exception {
        mockMvc.perform(put("/api/platform/accounts/1")
                        .contentType("application/json")
                        .content("{\"username\":\"u\",\"password\":\"p\",\"accountName\":\"n\",\"platformType\":\"GOV_PROCUREMENT\"}"))
                .andExpect(status().isForbidden());
    }
}
