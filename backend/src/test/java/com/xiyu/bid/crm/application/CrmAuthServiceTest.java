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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link CrmAuthService} 按用户 OSS token 换 CRM JWT（CO-152：无全局 03595）。
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
    @DisplayName("用户A配了crmSalesNo + 有用户OSS → 用用户OSS换专属CRM JWT")
    void getValidTokenForUser_userWithCrmSalesNo_returnsPerUserToken() {
        User userA = User.builder()
                .id(1L).username("userA").fullName("用户A").crmSalesNo("10001").build();
        when(userRepository.findByUsername("userA")).thenReturn(Optional.of(userA));
        putUserOss("userA", "oss-token-userA");
        mockGenerateTokenSuccess("crm-jwt-userA-10001");

        String token = authService.getValidTokenForUser("userA");

        assertThat(token).isEqualTo("crm-jwt-userA-10001");
        verify(httpClient).postWithAuth(
                anyString(), anyString(), eq("oss-token-userA"),
                org.mockito.ArgumentMatchers.contains("10001"));
    }

    @Test
    @DisplayName("用户B没配crmSalesNo → salesNo 用 username，OSS 仍是用户自己的")
    void getValidTokenForUser_userWithoutCrmSalesNo_usesUsernameAsSalesNo() {
        User userB = User.builder()
                .id(2L).username("userB").fullName("用户B").crmSalesNo(null).build();
        when(userRepository.findByUsername("userB")).thenReturn(Optional.of(userB));
        putUserOss("userB", "oss-token-userB");
        mockGenerateTokenSuccess("crm-jwt-userB");

        String token = authService.getValidTokenForUser("userB");

        assertThat(token).isEqualTo("crm-jwt-userB");
        verify(httpClient).postWithAuth(
                anyString(), anyString(), eq("oss-token-userB"),
                org.mockito.ArgumentMatchers.contains("\"salesNo\":\"userB\""));
    }

    @Test
    @DisplayName("用户A/B 各自 OSS + JWT 隔离")
    void getValidTokenForUser_userAAndUserB_isolated() {
        User userA = User.builder()
                .id(1L).username("userA").fullName("用户A").crmSalesNo("10001").build();
        User userB = User.builder()
                .id(2L).username("userB").fullName("用户B").crmSalesNo(null).build();
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
    }

    @Test
    @DisplayName("同一用户复用 CRM JWT 缓存")
    void getValidTokenForUser_sameUserReusesCachedToken() {
        User userA = User.builder()
                .id(1L).username("userA").fullName("用户A").crmSalesNo("10001").build();
        when(userRepository.findByUsername("userA")).thenReturn(Optional.of(userA));
        putUserOss("userA", "oss-token");
        mockGenerateTokenSuccess("crm-jwt-cached");

        String token1 = authService.getValidTokenForUser("userA");
        String token2 = authService.getValidTokenForUser("userA");

        assertThat(token1).isEqualTo(token2).isEqualTo("crm-jwt-cached");
        verify(httpClient, times(1)).postWithAuth(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("invalidate 后重新 generateToken")
    void getValidTokenForUser_cacheInvalidated_renewsToken() {
        User userA = User.builder()
                .id(1L).username("userA").fullName("用户A").crmSalesNo("10001").build();
        when(userRepository.findByUsername("userA")).thenReturn(Optional.of(userA));
        putUserOss("userA", "oss-token");
        mockGenerateTokenSequential("crm-jwt-1", "crm-jwt-2");

        String token1 = authService.getValidTokenForUser("userA");
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

        // B 仍缓存命中，不再调 generateToken；A 会再调一次
        authService.getValidTokenForUser("userB");
        verify(httpClient, times(2)).postWithAuth(anyString(), anyString(), anyString(), anyString());
        authService.getValidTokenForUser("userA");
        verify(httpClient, times(3)).postWithAuth(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("logoutUser 后重新 generate")
    void logoutUser_invalidatesCache() {
        User userA = User.builder()
                .id(1L).username("userA").fullName("用户A").crmSalesNo("10001").build();
        when(userRepository.findByUsername("userA")).thenReturn(Optional.of(userA));
        putUserOss("userA", "oss-token");
        mockGenerateTokenSequential("crm-jwt-before-logout", "crm-jwt-after-logout");

        authService.getValidTokenForUser("userA");
        authService.logoutUser("userA");
        String token = authService.getValidTokenForUser("userA");

        assertThat(token).isEqualTo("crm-jwt-after-logout");
        verify(httpClient, times(2)).postWithAuth(anyString(), anyString(), anyString(), anyString());
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
    @DisplayName("用户存在但无 OSS token → TokenUnavailableException")
    void getValidTokenForUser_noOssToken_throws() {
        User userA = User.builder()
                .id(1L).username("userA").fullName("用户A").crmSalesNo("10001").build();
        when(userRepository.findByUsername("userA")).thenReturn(Optional.of(userA));

        assertThatThrownBy(() -> authService.getValidTokenForUser("userA"))
                .isInstanceOf(TokenUnavailableException.class)
                .hasMessageContaining("OSS token not found");
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
        putUserOss("userA", "oss-token");
        mockGenerateTokenSuccess("crm-jwt-cached");

        authService.getValidTokenForUser("userA");
        authService.getValidTokenForUser("userA");

        verify(userRepository, times(1)).findByUsername("userA");
        verify(httpClient, times(1)).postWithAuth(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("D4-1: logoutUser 后重新查 DB")
    void getValidTokenForUser_afterLogoutUser_requeriesDb() {
        User user = User.builder()
                .id(1L).username("userA").fullName("用户A").crmSalesNo("10001").build();
        when(userRepository.findByUsername("userA")).thenReturn(Optional.of(user));
        putUserOss("userA", "oss-token");
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

    private void putUserOss(String username, String ossToken) {
        ossUserTokenCache.put(username, ossToken, 3600);
    }

    private void mockGenerateTokenSuccess(String crmJwtToken) {
        String response = String.format(
                "{\"code\":0,\"msg\":\"ok\",\"data\":\"%s\"}", crmJwtToken);
        when(httpClient.postWithAuth(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(CrmResponseHandler.parse(response));
    }

    private void mockGenerateTokenSequential(String... tokens) {
        CrmResponseHandler.CrmApiResponse[] responses = new CrmResponseHandler.CrmApiResponse[tokens.length];
        for (int i = 0; i < tokens.length; i++) {
            responses[i] = CrmResponseHandler.parse(String.format(
                    "{\"code\":0,\"msg\":\"ok\",\"data\":\"%s\"}", tokens[i]));
        }
        when(httpClient.postWithAuth(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(responses[0],
                        java.util.Arrays.copyOfRange(responses, 1, responses.length));
    }
}
