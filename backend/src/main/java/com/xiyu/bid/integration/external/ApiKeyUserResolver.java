package com.xiyu.bid.integration.external;

import com.xiyu.bid.apikey.application.ApiKeyService;
import com.xiyu.bid.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

/**
 * 从 Security Context 中的 principal 解析 API Key 对应的用户 ID（用于写入 updatedBy/creatorId）。
 *
 * <p>统一三个外部接口 Controller（TenderSync/TenderIntegration/ProjectIntegration）
 * 各自的私有解析实现，消除 copy-paste。解析失败（API Key 已删除、principal 畸形、
 * 属主用户不存在等）一律返回 null，不阻断主流程。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ApiKeyUserResolver {

    private static final String API_KEY_PREFIX = "api-key:";

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
        var keyOpt = apiKeyService.findById(apiKeyId);
        if (keyOpt.isEmpty()) {
            return null;
        }
        String createdBy = keyOpt.get().getCreatedBy();
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
}
