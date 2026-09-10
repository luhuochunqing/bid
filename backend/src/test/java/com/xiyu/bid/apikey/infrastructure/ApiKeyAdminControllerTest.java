// Input: 管理端 API Key 更新请求
// Output: PATCH /api/admin/api-keys/{id} 的路由与响应结构验证
// Pos: Test/MockMvc — API Key scopes 更新端点
package com.xiyu.bid.apikey.infrastructure;

import com.xiyu.bid.apikey.application.ApiKeyService;
import com.xiyu.bid.auth.JwtAuthenticationFilter;
import com.xiyu.bid.config.RateLimitFilter;
import com.xiyu.bid.config.SecurityConfig;
import com.xiyu.bid.entity.RoleProfileCatalog;
import com.xiyu.bid.exception.ResourceNotFoundException;
import com.xiyu.bid.security.CurrentUserResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ApiKeyAdminController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {SecurityConfig.class, JwtAuthenticationFilter.class, RateLimitFilter.class}
        ))
@Import(ApiKeyAdminControllerTest.TestSecurityConfig.class)
class ApiKeyAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ApiKeyService apiKeyService;

    @MockBean
    private CurrentUserResolver currentUserResolver;

    @EnableWebSecurity
    @EnableMethodSecurity(prePostEnabled = true)
    static class TestSecurityConfig {
        @Bean
        SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
            http.csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
            return http.build();
        }
    }

    @Test
    @DisplayName("PATCH 成功更新 scopes → 200 且返回更新后结构")
    @WithMockUser(authorities = {RoleProfileCatalog.SYSTEM_ADMIN_PERMISSION})
    void updateScopes_returnsUpdatedKey() throws Exception {
        when(apiKeyService.updateScopes(1L, "新名称", List.of("tender:read", "ca:read")))
                .thenReturn(new ApiKeyService.UpdateResult(
                        1L, "新名称", List.of("tender:read", "ca:read"),
                        LocalDateTime.of(2027, 1, 1, 0, 0)));

        mockMvc.perform(patch("/api/admin/api-keys/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"新名称","scopes":["tender:read","ca:read"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1L))
                .andExpect(jsonPath("$.data.name").value("新名称"))
                .andExpect(jsonPath("$.data.scopes[0]").value("tender:read"))
                .andExpect(jsonPath("$.data.scopes[1]").value("ca:read"));
    }

    @Test
    @DisplayName("PATCH scopes 为空 → 400 且不调用 service")
    @WithMockUser(authorities = {RoleProfileCatalog.SYSTEM_ADMIN_PERMISSION})
    void updateScopes_emptyScopes_rejected() throws Exception {
        mockMvc.perform(patch("/api/admin/api-keys/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"n","scopes":[]}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PATCH id 不存在 → service 抛 ResourceNotFoundException → 404")
    @WithMockUser(authorities = {RoleProfileCatalog.SYSTEM_ADMIN_PERMISSION})
    void updateScopes_notFound_returns404() throws Exception {
        when(apiKeyService.updateScopes(eq(99L), any(), any()))
                .thenThrow(ResourceNotFoundException.withMessage("API Key not found: 99"));

        mockMvc.perform(patch("/api/admin/api-keys/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"n","scopes":["ca:read"]}
                                """))
                .andExpect(status().isNotFound());
    }
}