package com.xiyu.bid.tender.controller;

import com.xiyu.bid.apikey.infrastructure.ApiKeyAuthenticationFilter;
import com.xiyu.bid.auth.JwtAuthenticationFilter;
import com.xiyu.bid.config.PaginationConstants;
import com.xiyu.bid.config.RateLimitFilter;
import com.xiyu.bid.config.SecurityConfig;
import com.xiyu.bid.demo.service.DemoDataProvider;
import com.xiyu.bid.demo.service.DemoFusionService;
import com.xiyu.bid.demo.service.DemoModeService;
import com.xiyu.bid.security.CurrentUserResolver;
import com.xiyu.bid.security.EffectiveRoleResolver;
import com.xiyu.bid.service.AuthService;
import com.xiyu.bid.tender.service.TenderAuditService;
import com.xiyu.bid.tender.service.TenderCommandService;
import com.xiyu.bid.tender.service.TenderImportService;
import com.xiyu.bid.tender.service.TenderMapper;
import com.xiyu.bid.tender.service.TenderQueryService;
import com.xiyu.bid.tender.service.TenderSearchCriteria;
import com.xiyu.bid.tender.service.TenderSubmissionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 回归验证 GET /api/tenders 的分页 size 上限保护（2026-08-02 OOM 根因修复）。
 *
 * <p>前端工作台曾传 size=10000 导致大查询；本次修复将 size 限制到
 * {@link PaginationConstants#MAX_PAGE_SIZE}（100），size<=0 时回退到
 * {@link PaginationConstants#DEFAULT_PAGE_SIZE}（20）。
 */
@WebMvcTest(controllers = TenderController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {SecurityConfig.class, JwtAuthenticationFilter.class, RateLimitFilter.class,
                        ApiKeyAuthenticationFilter.class}
        ))
@Import(TenderControllerSizeCapTest.TestSecurityConfig.class)
class TenderControllerSizeCapTest {

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
    private com.xiyu.bid.tender.service.TenderImportAppService tenderImportAppService;
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
    @MockBean
    private CurrentUserResolver currentUserResolver;
    @MockBean
    private EffectiveRoleResolver effectiveRoleResolver;

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

    private void stubEmptySearchResult() {
        when(tenderQueryService.searchTendersPaged(any(), any())).thenReturn(new PageImpl<>(List.of()));
        when(demoModeService.isEnabled()).thenReturn(false);
    }

    @Test
    @DisplayName("size=10000（超过上限）→ 传给 service 的 PageRequest size 被 clamp 到 100")
    @WithMockUser(roles = "MANAGER")
    void oversizedSize_shouldBeClampedToMaxPageSize() throws Exception {
        stubEmptySearchResult();

        mockMvc.perform(get("/api/tenders").param("size", "10000"))
                .andExpect(status().isOk());

        ArgumentCaptor<PageRequest> pageRequestCaptor = ArgumentCaptor.forClass(PageRequest.class);
        verify(tenderQueryService).searchTendersPaged(any(TenderSearchCriteria.class), pageRequestCaptor.capture());
        assertEquals(PaginationConstants.MAX_PAGE_SIZE, pageRequestCaptor.getValue().getPageSize());
    }

    @Test
    @DisplayName("size=0（非法/空）→ 传给 service 的 PageRequest size 回退到默认 20")
    @WithMockUser(roles = "MANAGER")
    void zeroSize_shouldFallbackToDefaultPageSize() throws Exception {
        stubEmptySearchResult();

        mockMvc.perform(get("/api/tenders").param("size", "0"))
                .andExpect(status().isOk());

        ArgumentCaptor<PageRequest> pageRequestCaptor = ArgumentCaptor.forClass(PageRequest.class);
        verify(tenderQueryService).searchTendersPaged(any(TenderSearchCriteria.class), pageRequestCaptor.capture());
        assertEquals(PaginationConstants.DEFAULT_PAGE_SIZE, pageRequestCaptor.getValue().getPageSize());
    }

    @Test
    @DisplayName("size=50（合法范围）→ 不作修改，原样传递")
    @WithMockUser(roles = "MANAGER")
    void inRangeSize_shouldBePassedThrough() throws Exception {
        stubEmptySearchResult();

        mockMvc.perform(get("/api/tenders").param("size", "50"))
                .andExpect(status().isOk());

        ArgumentCaptor<PageRequest> pageRequestCaptor = ArgumentCaptor.forClass(PageRequest.class);
        verify(tenderQueryService).searchTendersPaged(any(TenderSearchCriteria.class), pageRequestCaptor.capture());
        assertEquals(50, pageRequestCaptor.getValue().getPageSize());
    }
}