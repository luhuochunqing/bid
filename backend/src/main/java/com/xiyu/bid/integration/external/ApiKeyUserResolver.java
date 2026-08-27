package com.xiyu.bid.integration.external;

import com.xiyu.bid.apikey.application.ApiKeyService;
import com.xiyu.bid.apikey.entity.ApiKey;
import com.xiyu.bid.apikey.infrastructure.ApiKeyAuthConstants;
import com.xiyu.bid.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 从 Security Context 解析 API Key 对应的用户 ID（用于写入 updatedBy/creatorId）。
 *
 * <p>统一三个外部接口 Controller（TenderSync/TenderIntegration/ProjectIntegration）
 * 各自的私有解析实现，消除 copy-paste。解析失败（API Key 已删除、principal 畸形、
 * 属主用户不存在等）一律返回 null，不阻断主流程。
 *
 * <p>主路径优先读 {@code authentication details}（{@code ApiKeyAuthenticationFilter}
 * 认证成功时透传的属主用户名，零额外查询）；详情缺失（如 JWT 会话或旧链路）时
 * 回退到从 principal 反查 API Key 表。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ApiKeyUserResolver {

    private static final String API_KEY_PREFIX = ApiKeyAuthConstants.API_KEY_PRINCIPAL_PREFIX;

    private final ApiKeyService apiKeyService;
    private final AuthService authService;

    /**
     * @return API Key 属主用户 ID；无法解析（API Key 已删除/畸形 principal/属主不存在）返回 null
     */
    public Long resolveUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) {
            return null;
        }
        String createdBy = extractCreatedByFromDetails(auth);
        if (createdBy == null) {
            // 详情未透传（兼容旧链路/JWT 会话）：回退到从 principal 反查 API Key 表
            createdBy = resolveCreatedByFromPrincipal(auth);
        }
        if (createdBy == null || createdBy.isBlank()) {
            return null;
        }
        try {
            return authService.resolveUserIdByUsername(createdBy.trim());
        } catch (UsernameNotFoundException e) {
            log.warn("API key user '{}' not found: {}", createdBy, e.getMessage());
            return null;
        }
    }

    /** 读认证过滤器透传的属主用户名；详情非 Map 或键缺失时返回 null。 */
    private String extractCreatedByFromDetails(Authentication auth) {
        if (auth.getDetails() instanceof Map<?, ?> details) {
            Object value = details.get(ApiKeyAuthConstants.DETAILS_CREATED_BY_KEY);
            return value instanceof String s ? s : null;
        }
        return null;
    }

    /** 回退路径：从 principal（"api-key:&lt;id&gt;"）反查 API Key 表取属主用户名。 */
    private String resolveCreatedByFromPrincipal(Authentication auth) {
        String principal = auth.getPrincipal().toString();
        if (!principal.startsWith(API_KEY_PREFIX)) {
            return null;
        }
        Long apiKeyId;
        try {
            apiKeyId = Long.parseLong(principal.substring(API_KEY_PREFIX.length()));
        } catch (NumberFormatException e) {
            log.warn("Malformed API key principal: {}", principal);
            return null;
        }
        return apiKeyService.findById(apiKeyId)
                .map(ApiKey::getCreatedBy)
                .orElse(null);
    }
}
