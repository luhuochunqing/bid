package com.xiyu.bid.crm.application;

import com.xiyu.bid.crm.config.CrmProperties;
import com.xiyu.bid.crm.infrastructure.CrmHttpClient;
import com.xiyu.bid.crm.infrastructure.CrmResponseHandler;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link CrmAuthService} CRM JWT 换取测试。
 * <p>spec 037 fallback 版：OSS token 存在时走 {@code postWithAuth}（原路径），
 * OSS token 缺失时 fallback 到 {@code postJson}（无 Authorization）。
 * <p>环境行为矩阵：
 * <ul>
 *   <li>生产 + 已登录（有 OSS token） → postWithAuth，正常</li>
 *   <li>生产 + 未登录（无 OSS token） → fallback postJson，治本</li>
 *   <li>测试 + 已登录（有 OSS token） → postWithAuth，正常</li>
 *   <li>测试 + 未登录（无 OSS token） → fallback postJson，但测试环境 generateToken 要求 Authorization → 失败（CRM 配置问题）</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CrmAuthServiceTest {

    @Mock private CrmHttpClient httpClient;
    @Mock private UserRepository userRepository;

    private CrmProperties properties;
    private CrmUserTokenCache userTokenCache;
    private OssUserTokenCache ossUserTokenCache;
    private CrmAuthService authService;

    @BeforeEach
    void setUp() {
        properties = new CrmProperties();
        properties.setBaseUrl("http://crm.example.com");
        properties.setAuthBaseUrl("http://oss.example.com");
        properties.setChanceBaseUrl("http://crm.example.com");
        properties.getAuth().setGenerateTokenPath("/common/inner/generateToken");
        userTokenCache = new CrmUserTokenCache();
        ossUserTokenCache = new OssUserTokenCache();
        UserProfileCache userProfileCache = new UserProfileCache(userRepository);
        authService = new CrmAuthService(httpClient, properties, userTokenCache,
                ossUserTokenCache, userProfileCache);
    }

    // ========== 场景 1：OSS token 存在 → 走 postWithAuth（原路径） ==========

    @Test
    @DisplayName("OSS token 存在 → 走 postWithAuth（原路径），不调 postJson")
    void getValidTokenForUser_withOssToken_usesPostWithAuth() {
        User userA = User.builder()
                .id(1L).username("userA").fullName("用户A").crmSalesNo("10001").build();
        when(userRepository.findByUsername("userA")).thenReturn(Optional.of(userA));
        putUserOss("userA", "oss-token-userA");
        mockGenerateTokenWithAuthSuccess("crm-jwt-userA-10001");

        String token = authService.getValidTokenForUser("userA");

        assertThat(token).isEqualTo("crm-jwt-userA-10001");
        verify(httpClient).postWithAuth(anyString(), anyString(), contains("oss-token-userA"), contains("10001"));
        verify(httpClient, times(0)).postJson(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("OSS token 存在 + 无 crmSalesNo → salesNo 用 username，仍走 postWithAuth")
    void getValidTokenForUser_withOssTokenNoSalesNo_usesPostWithAuthAndUsernameAsSalesNo() {
        User userB = User.builder()
                .id(2L).username("userB").fullName("用户B").crmSalesNo(null).build();
        when(userRepository.findByUsername("userB")).thenReturn(Optional.of(userB));
        putUserOss("userB", "oss-token-userB");
        mockGenerateTokenWithAuthSuccess("crm-jwt-userB");

        String token = authService.getValidTokenForUser("userB");

        assertThat(token).isEqualTo("crm-jwt-userB");
        verify(httpClient).postWithAuth(anyString(), anyString(), anyString(), contains("\"salesNo\":\"userB\""));
        verify(httpClient, times(0)).postJson(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("OSS token 存在：用户A/B 各自 JWT 隔离（走 postWithAuth）")
    void getValidTokenForUser_withOssToken_userAAndUserB_isolated() {
        User userA = User.builder()
                .id(1L).username("userA").fullName("用户A").crmSalesNo("10001").build();
        User userB = User.builder()
                .id(2L).username("userB").fullName("用户B").crmSalesNo("10002").build();
        when(userRepository.findByUsername("userA")).thenReturn(Optional.of(userA));
        when(userRepository.findByUsername("userB")).thenReturn(Optional.of(userB));
        putUserOss("userA", "oss-a");
        putUserOss("userB", "oss-b");
        when(httpClient.postWithAuth(anyString(), anyString(), anyString(), anyString()))
                .thenAnswer(inv -> {
                    String body = inv.getArgument(3);
                    String jwt = body.contains("10001") ? "jwt-a" : "jwt-b";
                    return CrmResponseHandler.parse(
                            String.format("{\"code\":0,\"msg\":\"ok\",\"data\":\"%s\"}", jwt));
                });

        String tokenA = authService.getValidTokenForUser("userA");
        String tokenB = authService.getValidTokenForUser("userB");

        assertThat(tokenA).isEqualTo("jwt-a");
        assertThat(tokenB).isEqualTo("jwt-b");
        verify(httpClient, times(0)).postJson(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("OSS token 存在：同一用户复用 CRM JWT 缓存")
    void getValidTokenForUser_withOssToken_sameUserReusesCachedToken() {
        User userA = User.builder()
                .id(1L).username("userA").fullName("用户A").crmSalesNo("10001").build();
        when(userRepository.findByUsername("userA")).thenReturn(Optional.of(userA));
        putUserOss("userA", "oss-token");
        mockGenerateTokenWithAuthSuccess("crm-jwt-cached");

        String token1 = authService.getValidTokenForUser("userA");
        String token2 = authService.getValidTokenForUser("userA");

        assertThat(token1).isEqualTo(token2).isEqualTo("crm-jwt-cached");
        verify(httpClient, times(1)).postWithAuth(anyString(), anyString(), anyString(), any());
    }

    // ========== 场景 2：OSS token 缺失 → fallback 到 postJson（无 Authorization） ==========

    @Test
    @DisplayName("OSS token 缺失 → fallback 到 postJson（无 Authorization），仍能换 JWT")
    void getValidTokenForUser_noOssToken_fallsBackToPostJson() {
        User userA = User.builder()
                .id(1L).username("userA").fullName("用户A").crmSalesNo("10001").build();
        when(userRepository.findByUsername("userA")).thenReturn(Optional.of(userA));
        // 不 putUserOss —— OSS token 缺失
        mockGenerateTokenPostJsonSuccess("crm-jwt-without-oss");

        String token = authService.getValidTokenForUser("userA");

        assertThat(token).isEqualTo("crm-jwt-without-oss");
        verify(httpClient).postJson(anyString(), anyString(), contains("10001"));
        verify(httpClient, times(0)).postWithAuth(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("OSS token 缺失 + 无 crmSalesNo → salesNo 用 username，走 postJson")
    void getValidTokenForUser_noOssTokenNoSalesNo_usesPostJsonAndUsernameAsSalesNo() {
        User userB = User.builder()
                .id(2L).username("userB").fullName("用户B").crmSalesNo(null).build();
        when(userRepository.findByUsername("userB")).thenReturn(Optional.of(userB));
        mockGenerateTokenPostJsonSuccess("crm-jwt-userB-fallback");

        String token = authService.getValidTokenForUser("userB");

        assertThat(token).isEqualTo("crm-jwt-userB-fallback");
        verify(httpClient).postJson(anyString(), anyString(), contains("\"salesNo\":\"userB\""));
        verify(httpClient, times(0)).postWithAuth(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("OSS token 缺失：用户A/B 各自 JWT 隔离（走 postJson fallback）")
    void getValidTokenForUser_noOssToken_userAAndUserB_isolated() {
        User userA = User.builder()
                .id(1L).username("userA").fullName("用户A").crmSalesNo("10001").build();
        User userB = User.builder()
                .id(2L).username("userB").fullName("用户B").crmSalesNo(null).build();
        when(userRepository.findByUsername("userA")).thenReturn(Optional.of(userA));
        when(userRepository.findByUsername("userB")).thenReturn(Optional.of(userB));
        when(httpClient.postJson(anyString(), anyString(), anyString()))
                .thenAnswer(inv -> {
                    String body = inv.getArgument(2);
                    String jwt = body.contains("10001") ? "jwt-a" : "jwt-b";
                    return CrmResponseHandler.parse(
                            String.format("{\"code\":0,\"msg\":\"ok\",\"data\":\"%s\"}", jwt));
                });

        String tokenA = authService.getValidTokenForUser("userA");
        String tokenB = authService.getValidTokenForUser("userB");

        assertThat(tokenA).isEqualTo("jwt-a");
        assertThat(tokenB).isEqualTo("jwt-b");
        verify(httpClient, times(0)).postWithAuth(anyString(), anyString(), anyString(), anyString());
    }

    // ========== 场景 3：401 联合清理 + fallback 路径切换 ==========

    @Test
    @DisplayName("spec 037 M4：401 不清 OSS cache，第二次仍走 postWithAuth（OSS 仍在）")
    void handleUnauthorizedForUser_preservesOssCache_usesPostWithAuthAgain() {
        User userA = User.builder()
                .id(1L).username("userA").fullName("用户A").crmSalesNo("10001").build();
        when(userRepository.findByUsername("userA")).thenReturn(Optional.of(userA));
        putUserOss("userA", "oss-token");
        // 第一次：OSS token 存在 → postWithAuth
        mockGenerateTokenWithAuthSequential("crm-jwt-1", "crm-jwt-after-401");

        authService.getValidTokenForUser("userA");
        assertThat(ossUserTokenCache.get("userA")).isPresent();
        verify(httpClient, times(1)).postWithAuth(anyString(), anyString(), anyString(), anyString());
        verify(httpClient, times(0)).postJson(anyString(), anyString(), any());

        // spec 037 Review M4：401 只清 JWT + profile，不清 OSS cache
        // 原因：CRM JWT 401 与 OSS token 独立，误清 OSS 会让"菜单直连 OSS 接口"
        // 在 CRM 401 后需要重新登录。OSS token 失效应由 OSS 接口自身 401 触发
        authService.handleUnauthorizedForUser("userA");
        assertThat(ossUserTokenCache.get("userA")).isPresent();

        // 第二次：OSS token 仍在 → 仍走 postWithAuth（不再 fallback 到 postJson）
        String token = authService.getValidTokenForUser("userA");

        assertThat(token).isEqualTo("crm-jwt-after-401");
        verify(httpClient, times(2)).postWithAuth(anyString(), anyString(), anyString(), anyString());
        verify(httpClient, times(0)).postJson(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("spec 037 M4：401 只清当前用户 JWT/profile，不影响其他用户；OSS cache 全部保留")
    void handleUnauthorizedForUser_onlyClearsCurrentUser_jwtCacheIsolated_ossPreserved() {
        User userA = User.builder()
                .id(1L).username("userA").fullName("用户A").crmSalesNo("10001").build();
        User userB = User.builder()
                .id(2L).username("userB").fullName("用户B").crmSalesNo("10002").build();
        when(userRepository.findByUsername("userA")).thenReturn(Optional.of(userA));
        when(userRepository.findByUsername("userB")).thenReturn(Optional.of(userB));
        putUserOss("userA", "oss-a");
        putUserOss("userB", "oss-b");
        when(httpClient.postWithAuth(anyString(), anyString(), anyString(), anyString()))
                .thenAnswer(inv -> {
                    String body = inv.getArgument(3);
                    String jwt = body.contains("10001") ? "jwt-a" : "jwt-b";
                    return CrmResponseHandler.parse(
                            String.format("{\"code\":0,\"msg\":\"ok\",\"data\":\"%s\"}", jwt));
                });

        authService.getValidTokenForUser("userA");
        authService.getValidTokenForUser("userB");
        authService.handleUnauthorizedForUser("userA");

        // spec 037 M4：OSS cache 不被清理（A 和 B 都仍在）
        assertThat(ossUserTokenCache.get("userA")).isPresent();
        assertThat(ossUserTokenCache.get("userB")).isPresent();

        // B 仍缓存命中，不再调 generateToken
        authService.getValidTokenForUser("userB");
        verify(httpClient, times(2)).postWithAuth(anyString(), anyString(), anyString(), anyString());

        // A 的 JWT 已清 → 重新调 postWithAuth（OSS 仍在，不走 postJson fallback）
        authService.getValidTokenForUser("userA");
        verify(httpClient, times(3)).postWithAuth(anyString(), anyString(), anyString(), anyString());
        verify(httpClient, times(0)).postJson(anyString(), anyString(), any());
    }

    // ========== 场景 4：logoutUser ==========

    @Test
    @DisplayName("logoutUser 后重新 generate（OSS token 仍在 → 走 postWithAuth）")
    void logoutUser_invalidatesCache_reusesOssToken() {
        User userA = User.builder()
                .id(1L).username("userA").fullName("用户A").crmSalesNo("10001").build();
        when(userRepository.findByUsername("userA")).thenReturn(Optional.of(userA));
        putUserOss("userA", "oss-token");
        mockGenerateTokenWithAuthSequential("crm-jwt-before-logout", "crm-jwt-after-logout");

        authService.getValidTokenForUser("userA");
        authService.logoutUser("userA");
        String token = authService.getValidTokenForUser("userA");

        assertThat(token).isEqualTo("crm-jwt-after-logout");
        verify(httpClient, times(2)).postWithAuth(anyString(), anyString(), anyString(), anyString());
        verify(httpClient, times(0)).postJson(anyString(), anyString(), any());
    }

    // ========== 场景 5：错误处理 ==========

    @Test
    @DisplayName("用户不存在 → TokenUnavailableException（无全局回退）")
    void getValidTokenForUser_userNotFound_throws() {
        when(userRepository.findByUsername("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.getValidTokenForUser("unknown"))
                .isInstanceOf(TokenUnavailableException.class)
                .hasMessageContaining("user not found");
    }

    @Test
    @DisplayName("username 为空 → TokenUnavailableException")
    void getValidTokenForUser_blankUsername_throws() {
        assertThatThrownBy(() -> authService.getValidTokenForUser(null))
                .isInstanceOf(TokenUnavailableException.class)
                .hasMessageContaining("empty");
        assertThatThrownBy(() -> authService.getValidTokenForUser("  "))
                .isInstanceOf(TokenUnavailableException.class);
    }

    // ========== 场景 6：缓存行为 ==========

    @Test
    @DisplayName("D4-1: profile 缓存命中只查一次 DB")
    void getValidTokenForUser_cachesUserProfile_avoidsRepeatedDbQuery() {
        User user = User.builder()
                .id(1L).username("userA").fullName("用户A").crmSalesNo("10001").build();
        when(userRepository.findByUsername("userA")).thenReturn(Optional.of(user));
        putUserOss("userA", "oss-token");
        mockGenerateTokenWithAuthSuccess("crm-jwt-cached");

        authService.getValidTokenForUser("userA");
        authService.getValidTokenForUser("userA");

        verify(userRepository, times(1)).findByUsername("userA");
        verify(httpClient, times(1)).postWithAuth(anyString(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("D4-1: logoutUser 后重新查 DB")
    void getValidTokenForUser_afterLogoutUser_requeriesDb() {
        User user = User.builder()
                .id(1L).username("userA").fullName("用户A").crmSalesNo("10001").build();
        when(userRepository.findByUsername("userA")).thenReturn(Optional.of(user));
        putUserOss("userA", "oss-token");
        mockGenerateTokenWithAuthSequential("crm-jwt-before", "crm-jwt-after");

        authService.getValidTokenForUser("userA");
        authService.logoutUser("userA");
        authService.getValidTokenForUser("userA");

        verify(userRepository, times(2)).findByUsername("userA");
    }

    @Test
    @DisplayName("getValidOssTokenForUser 返回用户缓存的 OSS token")
    void getValidOssTokenForUser_returnsCached() {
        putUserOss("userA", "oss-token-xyz");
        assertThat(authService.getValidOssTokenForUser("userA")).isEqualTo("oss-token-xyz");
    }

    // ========== 辅助方法 ==========

    private void putUserOss(String username, String ossToken) {
        ossUserTokenCache.put(username, ossToken, 3600);
    }

    private void mockGenerateTokenWithAuthSuccess(String crmJwtToken) {
        String response = String.format(
                "{\"code\":0,\"msg\":\"ok\",\"data\":\"%s\"}", crmJwtToken);
        when(httpClient.postWithAuth(anyString(), anyString(), anyString(), any()))
                .thenReturn(CrmResponseHandler.parse(response));
    }

    private void mockGenerateTokenWithAuthSequential(String... tokens) {
        CrmResponseHandler.CrmApiResponse[] responses = new CrmResponseHandler.CrmApiResponse[tokens.length];
        for (int i = 0; i < tokens.length; i++) {
            responses[i] = CrmResponseHandler.parse(String.format(
                    "{\"code\":0,\"msg\":\"ok\",\"data\":\"%s\"}", tokens[i]));
        }
        when(httpClient.postWithAuth(anyString(), anyString(), anyString(), any()))
                .thenReturn(responses[0],
                        java.util.Arrays.copyOfRange(responses, 1, responses.length));
    }

    private void mockGenerateTokenPostJsonSuccess(String crmJwtToken) {
        String response = String.format(
                "{\"code\":0,\"msg\":\"ok\",\"data\":\"%s\"}", crmJwtToken);
        when(httpClient.postJson(anyString(), anyString(), any()))
                .thenReturn(CrmResponseHandler.parse(response));
    }
}
