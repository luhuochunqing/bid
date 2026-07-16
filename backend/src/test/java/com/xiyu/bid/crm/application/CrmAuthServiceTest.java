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
 * {@link CrmAuthService} 按用户 OSS token 换 CRM JWT（CO-152：无全局 03595）。
 * <p>spec 037：去掉 OSS token 依赖，generateToken 改用 {@link CrmHttpClient#postJson}（无 Authorization）。
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

    @Test
    @DisplayName("用户A配了crmSalesNo → 用 nickName+salesNo 换专属 CRM JWT（spec 037: postJson）")
    void getValidTokenForUser_userWithCrmSalesNo_returnsPerUserToken() {
        User userA = User.builder()
                .id(1L).username("userA").fullName("用户A").crmSalesNo("10001").build();
        when(userRepository.findByUsername("userA")).thenReturn(Optional.of(userA));
        mockGenerateTokenSuccess("crm-jwt-userA-10001");

        String token = authService.getValidTokenForUser("userA");

        assertThat(token).isEqualTo("crm-jwt-userA-10001");
        // spec 037: 不再传 OSS token，改用 postJson
        verify(httpClient).postJson(anyString(), anyString(), contains("10001"));
        verify(httpClient, times(0)).postWithAuth(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("用户B没配crmSalesNo → salesNo 用 username（spec 037: postJson）")
    void getValidTokenForUser_userWithoutCrmSalesNo_usesUsernameAsSalesNo() {
        User userB = User.builder()
                .id(2L).username("userB").fullName("用户B").crmSalesNo(null).build();
        when(userRepository.findByUsername("userB")).thenReturn(Optional.of(userB));
        mockGenerateTokenSuccess("crm-jwt-userB");

        String token = authService.getValidTokenForUser("userB");

        assertThat(token).isEqualTo("crm-jwt-userB");
        verify(httpClient).postJson(anyString(), anyString(), contains("\"salesNo\":\"userB\""));
    }

    @Test
    @DisplayName("用户A/B 各自 JWT 隔离（spec 037: postJson）")
    void getValidTokenForUser_userAAndUserB_isolated() {
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
    }

    @Test
    @DisplayName("同一用户复用 CRM JWT 缓存")
    void getValidTokenForUser_sameUserReusesCachedToken() {
        User userA = User.builder()
                .id(1L).username("userA").fullName("用户A").crmSalesNo("10001").build();
        when(userRepository.findByUsername("userA")).thenReturn(Optional.of(userA));
        mockGenerateTokenSuccess("crm-jwt-cached");

        String token1 = authService.getValidTokenForUser("userA");
        String token2 = authService.getValidTokenForUser("userA");

        assertThat(token1).isEqualTo(token2).isEqualTo("crm-jwt-cached");
        verify(httpClient, times(1)).postJson(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("invalidate 后重新 generateToken（spec 037: 无需重新 putUserOss）")
    void getValidTokenForUser_cacheInvalidated_renewsToken() {
        User userA = User.builder()
                .id(1L).username("userA").fullName("用户A").crmSalesNo("10001").build();
        when(userRepository.findByUsername("userA")).thenReturn(Optional.of(userA));
        mockGenerateTokenSequential("crm-jwt-1", "crm-jwt-2");

        String token1 = authService.getValidTokenForUser("userA");
        // spec 037: 401 清理后无需重新登录拿 OSS token，可直接重新 generateToken
        authService.handleUnauthorizedForUser("userA");
        String token2 = authService.getValidTokenForUser("userA");

        assertThat(token1).isEqualTo("crm-jwt-1");
        assertThat(token2).isEqualTo("crm-jwt-2");
    }

    @Test
    @DisplayName("401 只清当前用户缓存，不影响其他用户")
    void handleUnauthorizedForUser_onlyClearsCurrentUser() {
        User userA = User.builder()
                .id(1L).username("userA").fullName("用户A").crmSalesNo("10001").build();
        User userB = User.builder()
                .id(2L).username("userB").fullName("用户B").crmSalesNo("10002").build();
        when(userRepository.findByUsername("userA")).thenReturn(Optional.of(userA));
        when(userRepository.findByUsername("userB")).thenReturn(Optional.of(userB));
        when(httpClient.postJson(anyString(), anyString(), anyString()))
                .thenAnswer(inv -> {
                    String body = inv.getArgument(2);
                    String jwt = body.contains("10001") ? "jwt-a" : "jwt-b";
                    return CrmResponseHandler.parse(
                            String.format("{\"code\":0,\"msg\":\"ok\",\"data\":\"%s\"}", jwt));
                });

        authService.getValidTokenForUser("userA");
        authService.getValidTokenForUser("userB");
        authService.handleUnauthorizedForUser("userA");

        // B 仍缓存命中，不再调 generateToken；A 会再调一次（spec 037: 无需 OSS token）
        authService.getValidTokenForUser("userB");
        verify(httpClient, times(2)).postJson(anyString(), anyString(), any());
        authService.getValidTokenForUser("userA");
        verify(httpClient, times(3)).postJson(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("logoutUser 后重新 generate")
    void logoutUser_invalidatesCache() {
        User userA = User.builder()
                .id(1L).username("userA").fullName("用户A").crmSalesNo("10001").build();
        when(userRepository.findByUsername("userA")).thenReturn(Optional.of(userA));
        mockGenerateTokenSequential("crm-jwt-before-logout", "crm-jwt-after-logout");

        authService.getValidTokenForUser("userA");
        authService.logoutUser("userA");
        String token = authService.getValidTokenForUser("userA");

        assertThat(token).isEqualTo("crm-jwt-after-logout");
        verify(httpClient, times(2)).postJson(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("用户不存在 → TokenUnavailableException（无全局回退）")
    void getValidTokenForUser_userNotFound_throws() {
        when(userRepository.findByUsername("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.getValidTokenForUser("unknown"))
                .isInstanceOf(TokenUnavailableException.class)
                .hasMessageContaining("user not found");
    }

    @Test
    @DisplayName("spec 037: 用户存在但无 OSS token → 仍能换 CRM JWT（postJson 不依赖 OSS）")
    void getValidTokenForUser_noOssToken_stillWorksViaPostJson() {
        User userA = User.builder()
                .id(1L).username("userA").fullName("用户A").crmSalesNo("10001").build();
        when(userRepository.findByUsername("userA")).thenReturn(Optional.of(userA));
        // 不 putUserOss —— OSS token 缺失
        mockGenerateTokenSuccess("crm-jwt-without-oss");

        String token = authService.getValidTokenForUser("userA");

        assertThat(token).isEqualTo("crm-jwt-without-oss");
        verify(httpClient).postJson(anyString(), anyString(), anyString());
        verify(httpClient, times(0)).postWithAuth(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("username 为空 → TokenUnavailableException")
    void getValidTokenForUser_blankUsername_throws() {
        assertThatThrownBy(() -> authService.getValidTokenForUser(null))
                .isInstanceOf(TokenUnavailableException.class);
        assertThatThrownBy(() -> authService.getValidTokenForUser("  "))
                .isInstanceOf(TokenUnavailableException.class);
    }

    @Test
    @DisplayName("D4-1: profile 缓存命中只查一次 DB")
    void getValidTokenForUser_cachesUserProfile_avoidsRepeatedDbQuery() {
        User user = User.builder()
                .id(1L).username("userA").fullName("用户A").crmSalesNo("10001").build();
        when(userRepository.findByUsername("userA")).thenReturn(Optional.of(user));
        mockGenerateTokenSuccess("crm-jwt-cached");

        authService.getValidTokenForUser("userA");
        authService.getValidTokenForUser("userA");

        verify(userRepository, times(1)).findByUsername("userA");
        verify(httpClient, times(1)).postJson(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("D4-1: logoutUser 后重新查 DB")
    void getValidTokenForUser_afterLogoutUser_requeriesDb() {
        User user = User.builder()
                .id(1L).username("userA").fullName("用户A").crmSalesNo("10001").build();
        when(userRepository.findByUsername("userA")).thenReturn(Optional.of(user));
        mockGenerateTokenSequential("crm-jwt-before", "crm-jwt-after");

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

    @Test
    @DisplayName("spec 037: 401 联合清理 JWT + profile + OSS；OSS 缺失也能用 postJson 重新换 JWT")
    void handleUnauthorizedForUser_clearsOssAndJwt() {
        User userA = User.builder()
                .id(1L).username("userA").fullName("用户A").crmSalesNo("10001").build();
        when(userRepository.findByUsername("userA")).thenReturn(Optional.of(userA));
        putUserOss("userA", "oss-token");
        mockGenerateTokenSuccess("crm-jwt-1");

        authService.getValidTokenForUser("userA");
        assertThat(ossUserTokenCache.get("userA")).isPresent();

        authService.handleUnauthorizedForUser("userA");

        // OSS 已被清理
        assertThat(ossUserTokenCache.get("userA")).isEmpty();
        // spec 037: OSS 缺失也能用 postJson 重新换 JWT（不再抛 TokenUnavailableException）
        mockGenerateTokenSuccess("crm-jwt-after-401");
        String token = authService.getValidTokenForUser("userA");
        assertThat(token).isEqualTo("crm-jwt-after-401");
        // 总共调 2 次 postJson：L269 首次换 JWT + L278 401 后重新换 JWT
        verify(httpClient, times(2)).postJson(anyString(), eq("/common/inner/generateToken"), anyString());
    }

    @Test
    @DisplayName("getValidTokenForUser(blank) 直接失败，无系统账号兜底")
    void getValidTokenForUser_blank_throws() {
        assertThatThrownBy(() -> authService.getValidTokenForUser(null))
                .isInstanceOf(TokenUnavailableException.class)
                .hasMessageContaining("empty");
        assertThatThrownBy(() -> authService.getValidTokenForUser("  "))
                .isInstanceOf(TokenUnavailableException.class);
    }

    private void putUserOss(String username, String ossToken) {
        ossUserTokenCache.put(username, ossToken, 3600);
    }

    private void mockGenerateTokenSuccess(String crmJwtToken) {
        String response = String.format(
                "{\"code\":0,\"msg\":\"ok\",\"data\":\"%s\"}", crmJwtToken);
        when(httpClient.postJson(anyString(), anyString(), any()))
                .thenReturn(CrmResponseHandler.parse(response));
    }

    private void mockGenerateTokenSequential(String... tokens) {
        CrmResponseHandler.CrmApiResponse[] responses = new CrmResponseHandler.CrmApiResponse[tokens.length];
        for (int i = 0; i < tokens.length; i++) {
            responses[i] = CrmResponseHandler.parse(String.format(
                    "{\"code\":0,\"msg\":\"ok\",\"data\":\"%s\"}", tokens[i]));
        }
        when(httpClient.postJson(anyString(), anyString(), any()))
                .thenReturn(responses[0],
                        java.util.Arrays.copyOfRange(responses, 1, responses.length));
    }

    private static String eq(String value) {
        return org.mockito.ArgumentMatchers.eq(value);
    }
}
