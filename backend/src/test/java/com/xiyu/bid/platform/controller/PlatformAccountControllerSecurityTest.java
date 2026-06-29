package com.xiyu.bid.platform.controller;

import com.xiyu.bid.apikey.infrastructure.ApiKeyAuthenticationFilter;
import com.xiyu.bid.auth.JwtAuthenticationFilter;
import com.xiyu.bid.config.RateLimitFilter;
import com.xiyu.bid.config.SecurityConfig;
import com.xiyu.bid.entity.User;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CO-389: 平台账户密码查看接口的权限放开回归门禁。
 *
 * <p>验证 {@code GET /api/platform/accounts/{id}/password} 的方法级
 * {@code @PreAuthorize} 表达式：admin / bidAdmin / bid-TeamLeader 通过，
 * bid-Team / sales（bid-projectLeader）拒绝。
 *
 * <p>本测试只覆盖 controller 层 {@code @PreAuthorize} 拒绝路径（403），
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

    // ── 拒绝路径（CO-389 维持原状） ────────────────────────────────────────

    @Test
    @DisplayName("bid-Team 不可查看密码（CO-388 操作项矩阵对齐）")
    @WithMockUser(authorities = {"bid-Team", "ROLE_BID_TEAM", "resource"})
    void bidTeam_GET_password_shouldReturn403() throws Exception {
        mockMvc.perform(get("/api/platform/accounts/1/password"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("sales（bid-projectLeader）不可查看密码（CO-388 操作项矩阵对齐）")
    @WithMockUser(authorities = {"bid-projectLeader", "ROLE_BID_PROJECTLEADER", "resource"})
    void sales_GET_password_shouldReturn403() throws Exception {
        mockMvc.perform(get("/api/platform/accounts/1/password"))
                .andExpect(status().isForbidden());
    }
}
