// Input: Spring 配置属性、环境变量、外部 bean 依赖
// Output: 配置 Bean、过滤器、线程池和启动级常量
// Pos: Config/基础设施层
// 维护声明: 仅维护配置与启动约束；业务规则变更请同步到对应 service/controller.
package com.xiyu.bid.config;

import com.xiyu.bid.auth.JwtAuthenticationFilter;
import com.xiyu.bid.auth.UserDetailsServiceImpl;
import com.xiyu.bid.idempotency.IdempotencyFilter;
import com.xiyu.bid.apikey.infrastructure.ApiKeyAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    private static final String CONTENT_SECURITY_POLICY = String.join("; ",
            "default-src 'self'",
            "script-src 'self'",
            "style-src 'self' 'unsafe-inline'",
            "img-src 'self' data:",
            "font-src 'self' data:",
            "connect-src 'self'",
            "frame-ancestors 'self'",
            "base-uri 'self'",
            "form-action 'self'"
    );
    private static final String PERMISSIONS_POLICY = String.join(", ",
            "camera=()",
            "microphone=()",
            "geolocation=()",
            "payment=()"
    );

    private final UserDetailsServiceImpl userDetailsService;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ObjectProvider<RateLimitFilter> rateLimitFilterProvider;
    private final IdempotencyFilter idempotencyFilter;
    private final Environment environment;
    private final ApiKeyAuthenticationFilter apiKeyAuthenticationFilter;

    private HttpSecurity addFilters(HttpSecurity http) throws Exception {
        RateLimitFilter rateLimitFilter = rateLimitFilterProvider.getIfAvailable();
        if (rateLimitFilter != null) {
            http.addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class);
        }
        return http
                .addFilterBefore(apiKeyAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(idempotencyFilter, JwtAuthenticationFilter.class);
    }

    @Value("${cors.allowed-origins:http://localhost:1314,http://127.0.0.1:1314,http://localhost:5173,http://localhost:5174,http://localhost:3000,https://winbid-test.ehsy.com,http://winbid-test.ehsy.com}")
    private String[] corsAllowedOrigins;

    private static final String[] WHITE_LIST_URL = {
            "/api/auth/login",
            "/api/auth/register",
            "/api/auth/logout",
            "/api/auth/refresh",
            "/api/auth/forgot-password",
            "/api/auth/reset-password",
            "/api/auth/wecom/**",
            // /api/auth/sessions intentionally removed from allowlist (H1 fix 2026-06-13):
            // 会话列表/撤销是认证后操作，必须走 anyRequest().authenticated() 兜底 + 方法级 @PreAuthorize。
            "/api/auth/verify-email/**",
            "/api/public/**",
            // NOTE: /api/integrations/organization/events removed — HTTP webhook path deleted per FR-012.
            // SDK path uses @AcceptEvent which bypasses the HTTP layer entirely.
            "/api/webhooks/crm/**",
            "/api/system/runtime-mode",
            "/api/integrations/oa/weaver/callback",
            "/actuator/health",
            "/actuator/health/**",
            "/api/external/**",
            "/api/systems/external/**",
            "/error"
    };

    private static final String[] DEV_ONLY_WHITE_LIST = {
            "/h2-console/**",
            "/v3/api-docs",
            "/v3/api-docs/**",
            "/v3/api-docs.yaml",
            "/swagger-ui.html",
            "/swagger-ui/**"
    };

    /**
     * Decides whether dev-only tooling endpoints (h2-console, swagger, api-docs) are
     * exposed anonymously. Mirrors the prod-like profile taxonomy enforced by the
     * dev-tooling shell guards (e.g. {@code backend/start.sh},
     * {@code scripts/dev-services.sh}): prod, production,
     * staging, stg, release, live, uat, canary all count as prod-like and disable
     * the dev allowlist regardless of any other profile (including dev/e2e).
     *
     * <p>Extracted as a static helper so the gate can be unit-tested without
     * booting a {@link SecurityFilterChain}.
     */
    static boolean shouldAllowDevTooling(String[] activeProfiles) {
        if (activeProfiles == null) {
            return false;
        }
        java.util.List<String> profiles = Arrays.asList(activeProfiles);
        boolean isProdLike = profiles.stream().anyMatch(p -> {
            if (p == null) {
                return false;
            }
            String v = p.toLowerCase();
            return v.equals("prod") || v.equals("production")
                    || v.equals("staging") || v.equals("stg")
                    || v.equals("release") || v.equals("live")
                    || v.equals("uat") || v.equals("canary");
        });
        if (isProdLike) {
            return false;
        }
        return profiles.contains("dev") || profiles.contains("e2e");
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        boolean devToolingAllowed = shouldAllowDevTooling(environment.getActiveProfiles());

        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers(WHITE_LIST_URL).permitAll();
                    if (devToolingAllowed) {
                        auth.requestMatchers(DEV_ONLY_WHITE_LIST).permitAll();
                    }
                    // ApiKeyAuthenticationFilter 在 UsernamePasswordAuthenticationFilter 之前运行，
                    // 对 /api/integration/** 与 /api/external/** 路径：无 X-API-Key 直接 sendError(401)
                    // （见 ApiKeyAuthenticationFilter#doFilterInternal,H7 fix 2026-06-13），
                    // 所以这里 permitAll 是为了让 filter 先处理,而非放行。
                    auth.requestMatchers("/api/integration/**", "/api/external/**").permitAll();
                    auth.requestMatchers("/api/admin/**").hasRole("ADMIN")
                            .requestMatchers("/api/manager/**").hasAnyRole("ADMIN", "MANAGER")
                            // 案例库（/api/cases/**）路径级兜底：要求 ADMIN/MANAGER/STAFF 任一角色
                            // 配合 @PreAuthorize 在方法级做更精细的角色 / 资源范围控制
                            .requestMatchers("/api/cases/**").hasAnyRole("ADMIN", "MANAGER", "STAFF")
                            .anyRequest().authenticated();
                })
                .authenticationProvider(authenticationProvider());
        addFilters(http);
        http.headers(headers -> headers
                .frameOptions(frameOptions -> frameOptions.sameOrigin())
                .contentSecurityPolicy(csp -> csp.policyDirectives(CONTENT_SECURITY_POLICY))
                .httpStrictTransportSecurity(hsts -> hsts
                        .includeSubDomains(true)
                        .preload(true)
                        .maxAgeInSeconds(31536000))
                .referrerPolicy(referrer -> referrer
                        .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                .permissionsPolicy(permissions -> permissions.policy(PERMISSIONS_POLICY))
        );

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // 仅允许指定的前端域名（从环境变量配置）
        configuration.setAllowedOrigins(Arrays.asList(corsAllowedOrigins));

        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));

        // 明确指定允许的请求头，而不是使用通配符
        configuration.setAllowedHeaders(Arrays.asList(
            "Authorization",
            "Content-Type",
            "Accept",
            "X-Requested-With",
            "Idempotency-Key",
            "EHSY-TraceID",
            "EHSY-SRCAPP",
            "EHSY-Signature"
        ));

        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);  // 1小时

        // 暴露 Content-Disposition 头，使浏览器能读取下载文件名（CO-285）
        configuration.setExposedHeaders(Arrays.asList("Content-Disposition", "Content-Length"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
