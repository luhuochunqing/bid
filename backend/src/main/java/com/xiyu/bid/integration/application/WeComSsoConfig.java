package com.xiyu.bid.integration.application;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 企微 SSO 配置值对象（不可变）。
 * <p>由 {@link WeComIntegrationAppService#getSsoConfig()} 从 {@code wecom_integration} 表读取，
 * 供消息推送、SSO 登录等场景消费。null 或 {@link #isValid()} 为 false 时表示 SSO 未启用或配置不全。
 *
 * <p>同时承担 OAuth 授权链接的构造（Pure Core，无 IO 依赖），将 SSO URL 构造逻辑与 SSO 配置数据
 * 封装在一起，避免散落到多个 Service。
 *
 * @param corpId  企业 CorpID
 * @param agentId 自建应用 AgentID
 */
public record WeComSsoConfig(String corpId, String agentId) {

    private static final String OAUTH_AUTHORIZE_URL = "https://open.weixin.qq.com/connect/oauth2/authorize";
    private static final String OAUTH_SCOPE = "snsapi_base";
    private static final String OAUTH_RESPONSE_TYPE = "code";
    private static final String OAUTH_REDIRECT_PATH = "/login";
    private static final String OAUTH_REDIRECT_PARAM = "redirect";

    /**
     * 校验配置是否完整可用。
     * @return true 表示 corpId 和 agentId 均非空非空白
     */
    public boolean isValid() {
        return corpId != null && !corpId.isBlank()
            && agentId != null && !agentId.isBlank();
    }

    /**
     * 构造企微 OAuth2 静默授权链接。
     * <p>用户点击后企微会回调到 {@code <platformBaseUrl>/login?redirect=<targetPath>&code=xxx&state=<state>}，
     * 由前端 Login.vue 处理 code+state 完成自动登录并跳回 targetPath。
     *
     * @param platformBaseUrl 业务系统基础 URL（如 https://winbid-test.ehsy.com）
     * @param targetPath      用户最终应跳转的业务路径（如 /project/42）
     * @param state           CSRF state 令牌（由 OAuthStateService 生成，每条消息唯一）
     * @return 完整的 OAuth 授权 URL
     */
    public String buildAuthorizeUrl(String platformBaseUrl, String targetPath, String state) {
        String normalizedBase = normalizeBase(platformBaseUrl);
        // 1. 构造 login URL with redirect 参数（前端 Login.vue 解析 redirect 跳转）
        String loginUrlWithRedirect = normalizedBase + OAUTH_REDIRECT_PATH
            + "?" + OAUTH_REDIRECT_PARAM + "="
            + URLEncoder.encode(targetPath, StandardCharsets.UTF_8);
        // 2. 整个 loginUrl 作为 OAuth redirect_uri 二次编码
        String redirectUri = URLEncoder.encode(loginUrlWithRedirect, StandardCharsets.UTF_8);

        return OAUTH_AUTHORIZE_URL
            + "?appid=" + corpId
            + "&redirect_uri=" + redirectUri
            + "&response_type=" + OAUTH_RESPONSE_TYPE
            + "&scope=" + OAUTH_SCOPE
            + "&agentid=" + agentId
            + "&state=" + state
            + "#wechat_redirect";
    }

    private static String normalizeBase(String platformBaseUrl) {
        if (platformBaseUrl == null || platformBaseUrl.isBlank()) {
            return "";
        }
        return platformBaseUrl.endsWith("/")
            ? platformBaseUrl.substring(0, platformBaseUrl.length() - 1)
            : platformBaseUrl;
    }
}
