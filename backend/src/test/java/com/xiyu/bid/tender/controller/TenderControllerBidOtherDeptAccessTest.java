package com.xiyu.bid.tender.controller;

import com.xiyu.bid.apikey.infrastructure.ApiKeyAuthenticationFilter;
import com.xiyu.bid.auth.JwtAuthenticationFilter;
import com.xiyu.bid.config.RateLimitFilter;
import com.xiyu.bid.config.SecurityConfig;
import com.xiyu.bid.demo.service.DemoDataProvider;
import com.xiyu.bid.demo.service.DemoFusionService;
import com.xiyu.bid.demo.service.DemoModeService;
import com.xiyu.bid.service.AuthService;
import com.xiyu.bid.tender.service.TenderAuditService;
import com.xiyu.bid.tender.service.TenderCommandService;
import com.xiyu.bid.tender.service.TenderImportService;
import com.xiyu.bid.tender.service.TenderMapper;
import com.xiyu.bid.tender.service.TenderQueryService;
import com.xiyu.bid.tender.service.TenderSubmissionService;
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
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 端到端验证 #679/#680 的修复效果：跨部门协同人员（bid_other_dept）登录后不应能访问标讯中心。
 *
 * <p>bid_other_dept 的 authority 集为 {@code {bid_other_dept, ROLE_BID_OTHER_DEPT, task.view.own, task.handle.own}}
 * （{@link com.xiyu.bid.auth.UserDetailsServiceImpl} 已不再向其颁发 ROLE_STAFF）。
 * TenderController 类级与方法级 @PreAuthorize 的白名单均不含 BID_OTHER_DEPT，
 * 故对其所有 endpoint 应返回 403。本测试以 @WithMockUser(roles="BID_OTHER_DEPT")
 * 模拟该 authority 集，作为 regression gate：防止未来 @PreAuthorize 被误改为含 BID_OTHER_DEPT、
 * 或 authoritiesFor 回退到再次颁发 ROLE_STAFF。
 */
@WebMvcTest(controllers = TenderController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {SecurityConfig.class, JwtAuthenticationFilter.class, RateLimitFilter.class,
                        ApiKeyAuthenticationFilter.class}
        ))
@Import(TenderControllerBidOtherDeptAccessTest.TestSecurityConfig.class)
class TenderControllerBidOtherDeptAccessTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TenderQueryService tenderQueryService;
    @MockBean
    private TenderCommandService tenderCommandService;
    @MockBean
    private TenderSubmissionService tenderSubmissionService;
    @MockBean
    private TenderMapper tenderMapper;
    @MockBean
    private TenderImportService tenderImportService;
    @MockBean
    private DemoModeService demoModeService;
    @MockBean
    private DemoDataProvider demoDataProvider;
    @MockBean
    private DemoFusionService demoFusionService;
    @MockBean
    private TenderAuditService tenderAuditService;
    @MockBean
    private AuthService authService;

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

    @Test
    @DisplayName("跨部门协同人员访问标讯列表 → 403")
    @WithMockUser(roles = "BID_OTHER_DEPT")
    void listTenders_shouldReturn403_forBidOtherDept() throws Exception {
        mockMvc.perform(get("/api/tenders"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("跨部门协同人员访问标讯详情 → 403")
    @WithMockUser(roles = "BID_OTHER_DEPT")
    void getTenderById_shouldReturn403_forBidOtherDept() throws Exception {
        mockMvc.perform(get("/api/tenders/1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("回归：staff 仍可访问标讯列表（未被误伤）")
    @WithMockUser(roles = "STAFF")
    void listTenders_shouldSucceed_forStaff() throws Exception {
        when(tenderQueryService.searchTendersPaged(any(), any())).thenReturn(new PageImpl<>(List.of()));
        when(demoModeService.isEnabled()).thenReturn(false);

        mockMvc.perform(get("/api/tenders"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("CO-317：行政人员可只读访问标讯列表")
    @WithMockUser(roles = "ADMIN_STAFF")
    void listTenders_shouldSucceed_forAdminStaff() throws Exception {
        when(tenderQueryService.searchTendersPaged(any(), any())).thenReturn(new PageImpl<>(List.of()));
        when(demoModeService.isEnabled()).thenReturn(false);

        mockMvc.perform(get("/api/tenders"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("CO-317：行政人员仍不可创建标讯")
    @WithMockUser(roles = "ADMIN_STAFF")
    void createTender_shouldReturn403_forAdminStaff() throws Exception {
        mockMvc.perform(post("/api/tenders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "行政人员不应创建标讯",
                                  "deadline": "2026-12-31T18:00:00"
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("CO-317：行政人员仍不可删除标讯")
    @WithMockUser(roles = "ADMIN_STAFF")
    void deleteTender_shouldReturn403_forAdminStaff() throws Exception {
        mockMvc.perform(delete("/api/tenders/1"))
                .andExpect(status().isForbidden());
    }
}
