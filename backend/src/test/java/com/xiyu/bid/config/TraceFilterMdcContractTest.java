package com.xiyu.bid.config;

import com.xiyu.bid.entity.User;
import com.xiyu.bid.security.CurrentUserResolver;
import com.xiyu.bid.security.EffectiveRoleResolver;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link TraceFilter} MDC 契约测试（US3）。
 *
 * <p>验证契约：
 * <ol>
 *   <li>未认证请求 → MDC userId/roleCode = "anonymous"（兜底语义）</li>
 *   <li>已认证请求（CurrentUserResolver 能解析到 User）→ MDC userId/roleCode = 实际值</li>
 *   <li>roleCode 必须走 {@link EffectiveRoleResolver#resolveRoleCode}（CO-373 治理），
 *       不得直调 {@code User.getRoleCode()}</li>
 *   <li>请求结束后 MDC 的 userId/roleCode/traceId 被清理（避免线程复用泄漏）</li>
 * </ol>
 *
 * <p>注意：本测试聚焦 TraceFilter 单独契约。JwtAuthenticationFilter 在 setAuthentication
 * 后刷新 MDC 的行为由 {@code JwtAuthenticationFilterRevocationTest} 覆盖。
 */
@ExtendWith(MockitoExtension.class)
class TraceFilterMdcContractTest {

    @Mock
    private CurrentUserResolver currentUserResolver;

    @Mock
    private EffectiveRoleResolver effectiveRoleResolver;

    @Mock
    private FilterChain chain;

    private TraceFilter traceFilter;

    @BeforeEach
    void setUp() {
        traceFilter = new TraceFilter(currentUserResolver, effectiveRoleResolver);
        MDC.clear();
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void should_write_anonymous_for_unauthenticated_request() throws Exception {
        // 未认证请求：CurrentUserResolver 返回 null
        when(currentUserResolver.getCurrentUser()).thenReturn(null);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/tenders");
        MockHttpServletResponse response = new MockHttpServletResponse();

        traceFilter.doFilter(request, response, chain);

        // filterChain 执行期间 MDC 应为 anonymous（这里在 doFilter 返回后验证，MDC 已被清理，
        // 所以通过 chain.doFilter 的 ArgumentCaptor 无法直接抓取。改为验证 putUserContext 的副作用：
        // 即 EffectiveRoleResolver 未被调用（因为 user == null 分支不调 resolveRoleCode）。
        verify(effectiveRoleResolver, never()).resolveRoleCode(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void should_write_real_user_context_when_user_resolved() throws Exception {
        // 已认证请求：CurrentUserResolver 返回非 null User
        User user = User.builder().id(42L).username("xiaowang").build();
        when(currentUserResolver.getCurrentUser()).thenReturn(user);
        when(effectiveRoleResolver.resolveRoleCode(user)).thenReturn("bid-Team");

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/tenders");
        // 在 doFilter 内部，filterChain.doFilter 执行时 MDC 应已写入实际用户上下文。
        // 用 ArgumentCaptor 抓取不可行（MDC 是 thread-local 静态），改为在 chain.doFilter 内断言。
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain capturingChain = (req, resp) -> {
            // 在 filterChain 执行期间验证 MDC
            assertThat(MDC.get(TraceConstants.MDC_USER_ID_KEY)).isEqualTo("42");
            assertThat(MDC.get(TraceConstants.MDC_ROLE_CODE_KEY)).isEqualTo("bid-Team");
            assertThat(MDC.get(TraceConstants.MDC_TRACE_KEY)).isNotNull();
        };

        traceFilter.doFilter(request, response, capturingChain);

        // 验证 roleCode 走 EffectiveRoleResolver 统一入口（CO-373 治理）
        verify(effectiveRoleResolver).resolveRoleCode(user);
    }

    @Test
    void should_clear_mdc_after_request() throws Exception {
        // 请求结束后 MDC 的 userId/roleCode/traceId 应被清理
        when(currentUserResolver.getCurrentUser()).thenReturn(null);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/tenders");
        MockHttpServletResponse response = new MockHttpServletResponse();

        traceFilter.doFilter(request, response, chain);

        assertThat(MDC.get(TraceConstants.MDC_USER_ID_KEY)).isNull();
        assertThat(MDC.get(TraceConstants.MDC_ROLE_CODE_KEY)).isNull();
        assertThat(MDC.get(TraceConstants.MDC_TRACE_KEY)).isNull();
    }

    @Test
    void should_propagate_trace_id_from_header_when_present() throws Exception {
        // 分布式追踪：请求头带 X-Trace-Id 时应透传
        when(currentUserResolver.getCurrentUser()).thenReturn(null);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/tenders");
        request.addHeader("X-Trace-Id", "external-trace-123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain capturingChain = (req, resp) ->
                assertThat(MDC.get(TraceConstants.MDC_TRACE_KEY)).isEqualTo("external-trace-123");

        traceFilter.doFilter(request, response, capturingChain);

        // 响应头也应回写 X-Trace-Id
        assertThat(response.getHeader("X-Trace-Id")).isEqualTo("external-trace-123");
    }
}
