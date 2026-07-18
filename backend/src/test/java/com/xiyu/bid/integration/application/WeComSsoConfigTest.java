package com.xiyu.bid.integration.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * WeComSsoConfig 单元测试（Pure Core，无 IO 依赖）。
 * <p>验证 SSO 配置值对象的 isValid() 校验逻辑和 buildAuthorizeUrl() OAuth 链接构造逻辑。
 */
@DisplayName("WeComSsoConfig — SSO 配置值对象 + OAuth URL 构造（Pure Core）")
class WeComSsoConfigTest {

    private static final String CORP_ID = "wx045d055c4e7bab5e";
    private static final String AGENT_ID = "1000322";
    private static final String PLATFORM_BASE_URL = "https://winbid-test.ehsy.com";

    // ============ isValid() ============

    @Test
    @DisplayName("isValid: corpId 和 agentId 均非空 → true")
    void isValid_bothPresent_returnsTrue() {
        assertThat(new WeComSsoConfig(CORP_ID, AGENT_ID).isValid()).isTrue();
    }

    @Test
    @DisplayName("isValid: corpId 为 null → false")
    void isValid_nullCorpId_returnsFalse() {
        assertThat(new WeComSsoConfig(null, AGENT_ID).isValid()).isFalse();
    }

    @Test
    @DisplayName("isValid: corpId 为空白 → false")
    void isValid_blankCorpId_returnsFalse() {
        assertThat(new WeComSsoConfig("  ", AGENT_ID).isValid()).isFalse();
    }

    @Test
    @DisplayName("isValid: agentId 为 null → false")
    void isValid_nullAgentId_returnsFalse() {
        assertThat(new WeComSsoConfig(CORP_ID, null).isValid()).isFalse();
    }

    @Test
    @DisplayName("isValid: agentId 为空白 → false")
    void isValid_blankAgentId_returnsFalse() {
        assertThat(new WeComSsoConfig(CORP_ID, "").isValid()).isFalse();
    }

    // ============ buildAuthorizeUrl() ============

    @Test
    @DisplayName("buildAuthorizeUrl: 构造完整的 OAuth2 静默授权链接")
    void buildAuthorizeUrl_returnsFullOAuthUrl() {
        WeComSsoConfig config = new WeComSsoConfig(CORP_ID, AGENT_ID);

        String url = config.buildAuthorizeUrl(PLATFORM_BASE_URL, "/project/42", "msg:abc123");

        // 关键组成部分
        assertThat(url).startsWith("https://open.weixin.qq.com/connect/oauth2/authorize?");
        assertThat(url).endsWith("#wechat_redirect");
        assertThat(url).contains("appid=" + CORP_ID);
        assertThat(url).contains("agentid=" + AGENT_ID);
        assertThat(url).contains("response_type=code");
        assertThat(url).contains("scope=snsapi_base");
        assertThat(url).contains("state=msg:abc123");
    }

    @Test
    @DisplayName("buildAuthorizeUrl: redirect_uri 包含 /login?redirect=<targetPath>，且经过双重 URL 编码")
    void buildAuthorizeUrl_redirectUriContainsDoubleEncodedTargetPath() {
        WeComSsoConfig config = new WeComSsoConfig(CORP_ID, AGENT_ID);

        String url = config.buildAuthorizeUrl(PLATFORM_BASE_URL, "/project/42", "msg:state123");

        // targetPath "/project/42" 第一次 URLEncoder 编码为 %2Fproject%2F42（/ -> %2F）
        // 整个 loginUrl 再编码一次时，% 被编码为 %25，所以最终是 %252Fproject%252F42
        // 同时 "=" 也被编码为 %3D，所以 redirect= 变成 redirect%3D
        assertThat(url).contains("redirect%3D%252Fproject%252F42");
        // redirect_uri 参数存在
        assertThat(url).contains("redirect_uri=");
    }

    @Test
    @DisplayName("buildAuthorizeUrl: platformBaseUrl 末尾带 / 时正确去除（normalizeBase）")
    void buildAuthorizeUrl_baseUrlWithTrailingSlash_normalized() {
        WeComSsoConfig config = new WeComSsoConfig(CORP_ID, AGENT_ID);

        String urlWithSlash = config.buildAuthorizeUrl(
            PLATFORM_BASE_URL + "/", "/project/42", "msg:abc");
        String urlWithoutSlash = config.buildAuthorizeUrl(
            PLATFORM_BASE_URL, "/project/42", "msg:abc");

        // 末尾 / 会被 normalizeBase 去除，结果应该一致
        assertThat(urlWithSlash).isEqualTo(urlWithoutSlash);
    }

    @Test
    @DisplayName("buildAuthorizeUrl: targetPath 含特殊字符（如 ? =）时被正确编码")
    void buildAuthorizeUrl_targetPathWithSpecialChars_encoded() {
        WeComSsoConfig config = new WeComSsoConfig(CORP_ID, AGENT_ID);

        String url = config.buildAuthorizeUrl(PLATFORM_BASE_URL, "/project/42?tab=members", "msg:abc");

        // 第一次编码：? -> %3F, = -> %3D
        // 第二次编码：%3F -> %253F, %3D -> %253D
        assertThat(url).contains("%253Ftab%253Dmembers");
    }

    @Test
    @DisplayName("buildAuthorizeUrl: state 直接拼接到 URL（无需编码，state 由 OAuthStateService 生成 UUID）")
    void buildAuthorizeUrl_stateIsAppendedDirectly() {
        WeComSsoConfig config = new WeComSsoConfig(CORP_ID, AGENT_ID);
        String state = "msg:abc-123-def";

        String url = config.buildAuthorizeUrl(PLATFORM_BASE_URL, "/dashboard", state);

        assertThat(url).contains("state=" + state);
    }
}
