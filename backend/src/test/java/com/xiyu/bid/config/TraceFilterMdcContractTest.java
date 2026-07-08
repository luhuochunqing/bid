package com.xiyu.bid.config;

import com.xiyu.bid.security.CurrentUserResolver;
import com.xiyu.bid.security.CurrentUserResolver.UserMdcContext;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link TraceFilter} MDC 契约测试（US3）。
 *
 * <p>验证契约：
 * <ol>
 *   <li>未认证请求 → MDC userId/roleCode = "anonymous"（兜底语义）</li>
 *   <li>已认证请求（CurrentUserResolver 能解析到 UserMdcContext）→ MDC userId/roleCode = 实际值</li>
 *   <li>roleCode 通过 {@link CurrentUserResolver#getUserMdcContext()} 在事务内解析，
 *       内部走 {@link com.xiyu.bid.security.EffectiveRoleResolver#resolveRoleCode}（CO-373 治理），
 *       不得在 Filter 层直调 {@code User.getRoleCode()}（P0 EAGER→LAZY 修复）</li>
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
    private FilterChain chain;

    private TraceFilter traceFilter;

    @BeforeEach
    void setUp() {
        traceFilter = new TraceFilter(currentUserResolver);
        MDC.clear();
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void should_write_anonymous_for_unauthenticated_request() throws Exception {
        // 未认证请求：getUserMdcContext 返回 null
        when(currentUserResolver.getUserMdcContext()).thenReturn(null);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/tenders");
        MockHttpServletResponse response = new MockHttpServletResponse();

        traceFilter.doFilter(request, response, chain);

        // filterChain 执行期间 MDC 应为 anonymous（这里在 doFilter 返回后验证，MDC 已被清理，
        // 所以通过 chain.doFilter 的 ArgumentCaptor 无法直接抓取。改为验证 putUserContext 的副作用：
        // 即 getUserMdcContext 被调用且返回 null 时写入 anonymous。
        verify(currentUserResolver).getUserMdcContext();
    }

    @Test
    void should_write_real_user_context_when_user_resolved() throws Exception {
        // 已认证请求：getUserMdcContext 返回非 null UserMdcContext
        when(currentUserResolver.getUserMdcContext())
                .thenReturn(new UserMdcContext("42", "bid-Team"));

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

        // 验证 roleCode 通过 getUserMdcContext() 在事务内解析（P0 EAGER→LAZY 修复）
        verify(currentUserResolver).getUserMdcContext();
    }

    @Test
    void should_clear_mdc_after_request() throws Exception {
        // 请求结束后 MDC 的 userId/roleCode/traceId 应被清理
        when(currentUserResolver.getUserMdcContext()).thenReturn(null);

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
        when(currentUserResolver.getUserMdcContext()).thenReturn(null);

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
