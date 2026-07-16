package com.xiyu.bid.crm.application;

import com.xiyu.bid.crm.infrastructure.CrmHttpClient;
import com.xiyu.bid.crm.infrastructure.CrmResponseHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.function.Function;

/**
 * CRM API 调用模板（spec 037 Review L1）。
 * <p>收敛 6 个 Service 中重复的"调接口 → 401 → handleUnauthorizedForUser → 重新取 token → 再调"样板。
 * <p>统一异常契约：{@code getValidTokenForUser} 抛 {@link TokenUnavailableException} 或
 * {@link IllegalStateException} 时，由 {@link #executeWithTokenRetry} 捕获并返回调用方提供的 fallback。
 * <p>降级策略由调用方决定：emptyPageResult / null / CrmApiResponse(401,...) / 抛 BusinessException 等。
 * <p>用法示例：
 * <pre>{@code
 * CrmApiResponse response = apiTemplate.executeWithTokenRetry(
 *     username,
 *     token -> httpClient.post(baseUrl, path, token, body),
 *     "chance page-list"
 * );
 * }</pre>
 */
@Service
public class CrmApiTemplate {

    private static final Logger log = LoggerFactory.getLogger(CrmApiTemplate.class);

    private final CrmAuthService authService;

    public CrmApiTemplate(CrmAuthService authService) {
        this.authService = authService;
    }

    /**
     * 带 401 重试的 CRM API 调用模板。
     * <p>流程：
     * <ol>
     *   <li>取 token（失败时返回 {@code fallbackOnTokenUnavailable}）</li>
     *   <li>调 {@code apiCall} 执行业务请求</li>
     *   <li>若 401：调 {@code handleUnauthorizedForUser} 清缓存 + 重新取 token + 再调一次</li>
     *   <li>重试后 401 或 token 仍失败：返回 {@code fallbackOnTokenUnavailable}</li>
     * </ol>
     *
     * @param username                   当前操作用户 username
     * @param apiCall                    业务请求函数（输入 token，返回 {@link CrmResponseHandler.CrmApiResponse}）
     * @param fallbackOnTokenUnavailable token 不可用时返回的 fallback 响应（如 401 响应或 null）
     * @param operationName              操作名（用于日志）
     * @return CRM API 响应；token 不可用时返回 {@code fallbackOnTokenUnavailable}
     */
    public CrmResponseHandler.CrmApiResponse executeWithTokenRetry(
            String username,
            Function<String, CrmResponseHandler.CrmApiResponse> apiCall,
            CrmResponseHandler.CrmApiResponse fallbackOnTokenUnavailable,
            String operationName) {
        String token = acquireTokenOrFallback(username, fallbackOnTokenUnavailable, operationName);
        if (token == null) {
            return fallbackOnTokenUnavailable;
        }

        CrmResponseHandler.CrmApiResponse response = apiCall.apply(token);

        // 401 → 清缓存 + 重新取 token + 再调一次
        if (response.isUnauthorized()) {
            authService.handleUnauthorizedForUser(username);
            log.info("CRM {} got 401, refreshing token and retrying for user={}", operationName, username);
            token = acquireTokenOrFallback(username, fallbackOnTokenUnavailable, operationName);
            if (token == null) {
                return fallbackOnTokenUnavailable;
            }
            response = apiCall.apply(token);
        }

        return response;
    }

    /**
     * 便捷重载：fallback 默认为 {@code null}（适用于返回 null 表示降级的方法，如 {@code getDetailById}）。
     */
    public CrmResponseHandler.CrmApiResponse executeWithTokenRetry(
            String username,
            Function<String, CrmResponseHandler.CrmApiResponse> apiCall,
            String operationName) {
        return executeWithTokenRetry(username, apiCall, null, operationName);
    }

    /**
     * 取 token，失败时返回 null（调用方根据 null 判断走 fallback）。
     * <p>统一捕获 {@link TokenUnavailableException} 和 {@link IllegalStateException}，
     * 解决原 6 处 catch 类型不一致的问题。
     */
    private String acquireTokenOrFallback(String username,
                                           CrmResponseHandler.CrmApiResponse fallback,
                                           String operationName) {
        try {
            return authService.getValidTokenForUser(username);
        } catch (TokenUnavailableException | IllegalStateException e) {
            log.warn("CRM {} skipped: token unavailable for username={}: {}", operationName, username, e.getMessage());
            return null;
        }
    }
}
