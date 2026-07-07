package com.xiyu.bid.user.controller;

import com.xiyu.bid.apikey.infrastructure.ApiKeyAuthenticationFilter;
import com.xiyu.bid.auth.JwtAuthenticationFilter;
import com.xiyu.bid.config.RateLimitFilter;
import com.xiyu.bid.config.SecurityConfig;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.security.CurrentUserLookupService;
import com.xiyu.bid.security.CurrentUserResolver;
import com.xiyu.bid.user.dto.AssignmentCandidateDTO;
import com.xiyu.bid.user.service.AssignmentCandidateAppService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AssignmentCandidateController HTTP 契约测试（TDD Red 阶段）。
 *
 * <p>被测类 {@link AssignmentCandidateController} 尚未实现，本测试编译失败属于预期行为。
 */
@WebMvcTest(controllers = AssignmentCandidateController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {SecurityConfig.class, JwtAuthenticationFilter.class, RateLimitFilter.class,
                        ApiKeyAuthenticationFilter.class}
        ))
@AutoConfigureMockMvc(addFilters = false)
class AssignmentCandidateControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AssignmentCandidateAppService appService;

    @MockBean
    private CurrentUserLookupService currentUserLookupService;

    // CO-373 回归修复：CurrentUserResolver 现依赖 EffectiveRoleResolver→RoleCodeCachePort，
    // @WebMvcTest 切片不实例化该链；TraceFilter(@Component) 又强依赖 CurrentUserResolver。
    // 此处 mock 整个 CurrentUserResolver 以满足 TraceFilter 注入，避免上下文加载失败。
    @MockBean
    private CurrentUserResolver currentUserResolver;

    // Phase 3 US3 修复：TraceFilter 现依赖 EffectiveRoleResolver（MDC userId/roleCode 填充），
    // @WebMvcTest 切片不实例化该 bean，需手动 mock 以满足 TraceFilter 注入。
    @MockBean
    private com.xiyu.bid.security.EffectiveRoleResolver effectiveRoleResolver;

    @Test
    @WithMockUser(roles = "MANAGER")
    @DisplayName("GET /api/users/assignable-candidates?context=task 返回 200")
    void findCandidates_TaskContext_Returns200() throws Exception {
        when(appService.findCandidates(any(), any())).thenReturn(List.of(
                new AssignmentCandidateDTO(2L, "张三", "E001", "bid_admin", "投标管理员", "D1", "一部", true)
        ));

        mockMvc.perform(get("/api/users/assignable-candidates").param("context", "task"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].userId").value(2))
                .andExpect(jsonPath("$.data[0].name").value("张三"));
    }

    @Test
    @WithMockUser(username = "manager", roles = "MANAGER")
    @DisplayName("GET /api/users/assignable-candidates 使用认证用户解析服务解析当前用户")
    void findCandidates_UsesCurrentUserLookupServiceToResolveCurrentUser() throws Exception {
        User currentUser = User.builder().id(1L).username("manager").fullName("当前用户").build();
        when(currentUserLookupService.requireUser(any(UserDetails.class))).thenReturn(currentUser);
        when(appService.findCandidates(any(), same(currentUser))).thenReturn(List.of());

        mockMvc.perform(get("/api/users/assignable-candidates").param("context", "tender"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(username = "ghost", roles = "MANAGER")
    @DisplayName("认证用户无法解析为业务用户时返回 401")
    void findCandidates_AuthenticatedUserMissing_Returns401() throws Exception {
        when(currentUserLookupService.requireUser(any(UserDetails.class)))
                .thenThrow(new AuthenticationServiceException("Authenticated user not found: ghost"));

        mockMvc.perform(get("/api/users/assignable-candidates").param("context", "tender"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    @DisplayName("GET /api/users/assignable-candidates（无 context）返回 400")
    void findCandidates_MissingContext_Returns400() throws Exception {
        mockMvc.perform(get("/api/users/assignable-candidates"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    @DisplayName("GET /api/users/assignable-candidates?context=invalid 返回 400")
    void findCandidates_InvalidContext_Returns400() throws Exception {
        mockMvc.perform(get("/api/users/assignable-candidates").param("context", "invalid"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("未认证请求返回 401")
    void findCandidates_Unauthenticated_Returns401() throws Exception {
        mockMvc.perform(get("/api/users/assignable-candidates").param("context", "task"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    @DisplayName("GET /api/users/assignable-candidates?context=task&deptCode=BID_DEPT 返回 200")
    void findCandidates_TaskContextWithDeptCode_Returns200() throws Exception {
        when(appService.findCandidates(any(), any())).thenReturn(List.of(
                new AssignmentCandidateDTO(2L, "张三", "E001", "bid_admin", "投标管理员", "BID_DEPT", "投标部", true)
        ));

        mockMvc.perform(get("/api/users/assignable-candidates")
                        .param("context", "task")
                        .param("deptCode", "BID_DEPT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].deptCode").value("BID_DEPT"));
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    @DisplayName("GET /api/users/assignable-candidates?context=task&roleCode=bid_admin 返回 200")
    void findCandidates_TaskContextWithRoleCode_Returns200() throws Exception {
        when(appService.findCandidates(any(), any())).thenReturn(List.of(
                new AssignmentCandidateDTO(2L, "张三", "E001", "bid_admin", "投标管理员", "D1", "一部", true)
        ));

        mockMvc.perform(get("/api/users/assignable-candidates")
                        .param("context", "task")
                        .param("roleCode", "bid_admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].roleCode").value("bid_admin"));
    }
}
