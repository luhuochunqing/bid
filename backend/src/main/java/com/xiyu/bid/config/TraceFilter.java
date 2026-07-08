// Input: HTTP 请求进入
// Output: MDC 注入 traceId / userId / roleCode，响应头回写 X-Trace-Id，请求结束后清理
// Pos: Config/基础设施层 — 结构化日志 traceId + 用户上下文支撑
package com.xiyu.bid.config;

import com.xiyu.bid.entity.User;
import com.xiyu.bid.security.CurrentUserResolver;
import com.xiyu.bid.security.EffectiveRoleResolver;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 每个 HTTP 请求注入唯一的 traceId 与当前用户上下文到 MDC，供 logback 结构化日志使用。
 * <p>traceId 也会通过响应头 X-Trace-Id 返回客户端，便于前后端问题串联。</p>
 * <p>用户上下文（userId、roleCode）从 Spring Security 解析，未认证时写入 anonymous。</p>
 * <p>通过最高优先级确保在 AccessLogFilter 之前执行，使访问日志也能拿到 traceId。</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceFilter extends OncePerRequestFilter {

    private static final String TRACE_ID_HEADER = "X-Trace-Id";
    private static final String MDC_KEY = "traceId";

    private final CurrentUserResolver currentUserResolver;
    private final EffectiveRoleResolver effectiveRoleResolver;

    public TraceFilter(CurrentUserResolver currentUserResolver,
                       EffectiveRoleResolver effectiveRoleResolver) {
        this.currentUserResolver = currentUserResolver;
        this.effectiveRoleResolver = effectiveRoleResolver;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // 优先从请求头获取（支持分布式追踪），否则生成新 ID
        String traceId = request.getHeader(TRACE_ID_HEADER);
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString().replace("-", "");
        }
        MDC.put(MDC_KEY, traceId);
        response.setHeader(TRACE_ID_HEADER, traceId);

        putUserContext();

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
            MDC.remove(TraceConstants.MDC_USER_ID_KEY);
            MDC.remove(TraceConstants.MDC_ROLE_CODE_KEY);
        }
    }

    /**
     * 排除不需要用户上下文的开销路径：Actuator、Swagger 静态资源、前端静态资源。
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri.startsWith("/actuator")
                || uri.startsWith("/swagger-ui")
                || uri.startsWith("/v3/api-docs")
                || uri.startsWith("/webjars")
                || uri.equals("/favicon.ico")
                || uri.startsWith("/static/")
                || uri.startsWith("/assets/");
    }

    /**
     * 写入用户上下文到 MDC（userId + roleCode）。
     *
     * <p>时序说明：本方法在 {@code filterChain.doFilter()} 之前调用，此时
     * JwtAuthenticationFilter 尚未执行，SecurityContextHolder 为空，
     * 因此本方法主要写入 {@code anonymous} 兜底，覆盖未认证请求（如登录、健康检查）。</p>
     *
     * <p><b>已认证请求的 MDC 由 JwtAuthenticationFilter 刷新</b>：
     * 该过滤器在 {@code setAuthentication} 之后立即调用
     * {@code MDC.put(MDC_USER_ID_KEY, ...)} 与 {@code MDC.put(MDC_ROLE_CODE_KEY, ...)}，
     * 覆盖此处写入的 anonymous 值，使后续业务日志能正确携带用户身份。</p>
     *
     * <p>角色码读取走 EffectiveRoleResolver 统一入口（CO-373 治理），
     * OSS 用户以缓存角色码为准，缓存未命中时 fail-closed 返回 null，不回退 "manager"。</p>
     */
    private void putUserContext() {
        User user = currentUserResolver.getCurrentUser();
        if (user != null) {
            MDC.put(TraceConstants.MDC_USER_ID_KEY, String.valueOf(user.getId()));
            MDC.put(TraceConstants.MDC_ROLE_CODE_KEY, effectiveRoleResolver.resolveRoleCode(user));
        } else {
            MDC.put(TraceConstants.MDC_USER_ID_KEY, "anonymous");
            MDC.put(TraceConstants.MDC_ROLE_CODE_KEY, "anonymous");
        }
    }
}
