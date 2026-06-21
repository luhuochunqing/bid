package com.xiyu.bid.apikey.infrastructure;

import com.xiyu.bid.apikey.application.ApiKeyService;
import com.xiyu.bid.apikey.entity.ApiKey;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static com.xiyu.bid.apikey.infrastructure.ApiKeyAuthConstants.API_KEY_HEADERS;
import static com.xiyu.bid.apikey.infrastructure.ApiKeyAuthConstants.API_KEY_PARAMS;

@Slf4j
@Component
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private final ApiKeyService apiKeyService;

    public ApiKeyAuthenticationFilter(ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestUri = request.getRequestURI();

        // CO-280: 优先从 Header 读取，回落到 URL 查询参数。
        // 浏览器点击下载链接时无法携带自定义 Header，通过 URL 参数实现认证。
        String rawKey = API_KEY_HEADERS.stream()
                .map(request::getHeader)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(null);

        if (rawKey == null || rawKey.isBlank()) {
            rawKey = API_KEY_PARAMS.stream()
                    .map(request::getParameter)
                    .filter(StringUtils::hasText)
                    .findFirst()
                    .orElse(null);
        }

        log.debug("ApiKeyFilter: uri={} hasKey={}", requestUri, StringUtils.hasText(rawKey));

        if (!isApiKeyPath(requestUri)) {
            chain.doFilter(request, response);
            return;
        }

        // H7 fix 2026-06-13: /api/integration/** 与 /api/external/** 都是外部 API 网关,
        // 必须带 X-API-Key 头或 api_key 查询参数才能继续。无 key 时直接 401,不再让请求穿透到 controller。
        if (!StringUtils.hasText(rawKey)) {
            log.warn("Missing API key header or query parameter for {}", requestUri);
            sendError(response, 401, "Missing X-API-Key header or api_key parameter");
            return;
        }

        Optional<ApiKey> keyOpt = apiKeyService.authenticate(rawKey);
        if (keyOpt.isEmpty()) {
            log.warn("Invalid or expired API Key for {}", requestUri);
            sendError(response, 401, "Invalid or expired API Key");
            return;
        }

        ApiKey key = keyOpt.get();
        List<SimpleGrantedAuthority> authorities = new java.util.ArrayList<>(parseScopes(key.getScopes()));
        authorities.add(new SimpleGrantedAuthority("ROLE_EXTERNAL_API"));

        log.debug("ApiKeyFilter: key={} scopes={} authorities={}", key.getId(), key.getScopes(), authorities);

        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(
                        "api-key:" + key.getId(),
                        List.of(),
                        authorities);
        SecurityContextHolder.getContext().setAuthentication(auth);
        log.info("API Key auth OK: id={} scopes={} uri={}", key.getId(), key.getScopes(), requestUri);

        chain.doFilter(request, response);
    }

    private boolean isApiKeyPath(String uri) {
        return uri.startsWith("/api/external/") || uri.startsWith("/api/integration/");
    }

    private void sendError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(
                "{\"success\":false,\"code\":" + status + ",\"msg\":\"" + message + "\"}");
    }

    private List<SimpleGrantedAuthority> parseScopes(String scopes) {
        if (scopes == null || scopes.isBlank()) return List.of();
        return Arrays.stream(scopes.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> new SimpleGrantedAuthority("SCOPE_" + s.replace(":", "_").toUpperCase()))
                .toList();
    }
}
