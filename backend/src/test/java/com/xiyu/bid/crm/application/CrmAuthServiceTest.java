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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link CrmAuthService} 按用户维度 CRM token 管理测试（CO-152）。
 * <p>覆盖 issue 测试要点：
 * <ol>
 *   <li>用户 A 配了工号、用户 B 没配 → A 用专属 token，B 用共享 token</li>
 *   <li>token 过期后自动重新 generate</li>
 *   <li>修改 crm_sales_no 后旧 token 立即失效（通过 invalidate 实现）</li>
 *   <li>401 只清当前用户缓存，不影响其他用户</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CrmAuthServiceTest {

    @Mock private CrmHttpClient httpClient;
    @Mock private OssPermissionCache permissionCache;
    @Mock private UserRepository userRepository;

    private CrmProperties properties;
    private CrmUserTokenCache userTokenCache;
    private CrmAuthService authService;

    @BeforeEach
    void setUp() {
        properties = new CrmProperties();
        properties.setBaseUrl("http://crm.example.com");
        properties.setAuthBaseUrl("http://oss.example.com");
        properties.setChanceBaseUrl("http://crm.example.com");
        properties.setGenerateTokenNickName("global-nick");
        properties.setGenerateTokenSalesNo("03595");
        properties.getAuth().setGenerateTokenPath("/common/inner/generateToken");
        properties.getAuth().setOauthLoginPath("/oauth/login");
        // 内存模式（无 Redis）
        userTokenCache = new CrmUserTokenCache();
        authService = new CrmAuthService(httpClient, properties, permissionCache,
                userTokenCache, userRepository);
    }

    // ===== Issue 测试要点 #1: 配了工号用专属 token，没配用共享 token =====

    @Test
    @DisplayName("用户A配了crmSalesNo → 返回专属token，不等于全局共享token")
    void getValidTokenForUser_userWithCrmSalesNo_returnsPerUserToken() {
        // Arrange: 用户 A 配了工号
        User userA = User.builder()
                .id(1L).username("userA").fullName("用户A").crmSalesNo("10001").build();
        when(userRepository.findByUsername("userA")).thenReturn(Optional.of(userA));
        // mock OSS token 获取成功
        mockOssLoginSuccess("oss-token-xxx");
        // mock generateToken 返回用户 A 的专属 token
        mockGenerateTokenSuccess("crm-jwt-userA-10001");

        // Act
        String token = authService.getValidTokenForUser("userA");

        // Assert
        assertThat(token).isEqualTo("crm-jwt-userA-10001");
        // 验证 generateToken 调用时使用的是用户 A 的工号（非全局 03595）
        verify(httpClient).postWithAuth(
                anyString(), anyString(), eq("oss-token-xxx"),
                org.mockito.ArgumentMatchers.contains("10001"));
    }

    @Test
    @DisplayName("用户B没配crmSalesNo → 回退到全局共享token（当前行为）")
    void getValidTokenForUser_userWithoutCrmSalesNo_fallsBackToSharedToken() {
        // Arrange: 用户 B 没配工号
        User userB = User.builder()
                .id(2L).username("userB").fullName("用户B").crmSalesNo(null).build();
        when(userRepository.findByUsername("userB")).thenReturn(Optional.of(userB));
        // mock 全局共享 token 路径（OSS + generateToken）
        mockOssLoginSuccess("oss-token-shared");
        mockGenerateTokenSuccess("crm-jwt-shared-03595");

        // Act
        String token = authService.getValidTokenForUser("userB");

        // Assert
        assertThat(token).isEqualTo("crm-jwt-shared-03595");
        // 验证 generateToken 调用时使用的是全局工号 03595
        verify(httpClient).postWithAuth(
                anyString(), anyString(), eq("oss-token-shared"),
                org.mockito.ArgumentMatchers.contains("03595"));
    }

    @Test
    @DisplayName("用户A配了工号、用户B没配 → A用专属token，B用共享token，互不影响")
    void getValidTokenForUser_userAAndUserB_isolated() {
        User userA = User.builder()
                .id(1L).username("userA").fullName("用户A").crmSalesNo("10001").build();
        User userB = User.builder()
                .id(2L).username("userB").fullName("用户B").crmSalesNo(null).build();
        when(userRepository.findByUsername("userA")).thenReturn(Optional.of(userA));
        when(userRepository.findByUsername("userB")).thenReturn(Optional.of(userB));
        mockOssLoginSuccess("oss-token");
        // 按 body 内容区分返回不同 token：10001 → A 的专属，03595 → 全局共享
        mockGenerateTokenByBody("crm-jwt-A-10001", "crm-jwt-shared-03595");

        String tokenA = authService.getValidTokenForUser("userA");
        String tokenB = authService.getValidTokenForUser("userB");

        assertThat(tokenA).isEqualTo("crm-jwt-A-10001");
        assertThat(tokenB).isEqualTo("crm-jwt-shared-03595");
        assertThat(tokenA).isNotEqualTo(tokenB);
    }

    // ===== Issue 测试要点 #2: token 过期后自动重新 generate =====

    @Test
    @DisplayName("同一用户第二次调用 → 复用缓存token，不重新generate")
    void getValidTokenForUser_sameUserReusesCachedToken() {
        User user = User.builder()
                .id(1L).username("userA").fullName("用户A").crmSalesNo("10001").build();
        when(userRepository.findByUsername("userA")).thenReturn(Optional.of(user));
        mockOssLoginSuccess("oss-token");
        mockGenerateTokenSuccess("crm-jwt-cached");

        // 第一次调用：触发 generateToken
        String token1 = authService.getValidTokenForUser("userA");
        // 第二次调用：应复用缓存
        String token2 = authService.getValidTokenForUser("userA");

        assertThat(token1).isEqualTo("crm-jwt-cached");
        assertThat(token2).isEqualTo("crm-jwt-cached");
        // generateToken 只应被调用 1 次
        verify(httpClient, times(1)).postWithAuth(
                anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("用户缓存被清除后 → 重新generate新token")
    void getValidTokenForUser_cacheInvalidated_renewsToken() {
        User user = User.builder()
                .id(1L).username("userA").fullName("用户A").crmSalesNo("10001").build();
        when(userRepository.findByUsername("userA")).thenReturn(Optional.of(user));
        mockOssLoginSuccess("oss-token");
        // 按调用顺序返回不同 token：第一次 first，第二次 second
        mockGenerateTokenSequential("crm-jwt-first", "crm-jwt-second");

        // 第一次获取
        String token1 = authService.getValidTokenForUser("userA");
        // 清除缓存（模拟 401 或修改工号后失效）
        authService.handleUnauthorizedForUser("userA");
        // 再次获取应重新 generate
        String token2 = authService.getValidTokenForUser("userA");

        assertThat(token1).isEqualTo("crm-jwt-first");
        assertThat(token2).isEqualTo("crm-jwt-second");
        verify(httpClient, times(2)).postWithAuth(
                anyString(), anyString(), anyString(), anyString());
    }

    // ===== Issue 测试要点 #4: 401 只清当前用户缓存 =====

    @Test
    @DisplayName("handleUnauthorizedForUser 只清除当前用户缓存，不影响其他用户")
    void handleUnauthorizedForUser_clearsOnlyThatUserToken() {
        User userA = User.builder()
                .id(1L).username("userA").fullName("用户A").crmSalesNo("10001").build();
        User userB = User.builder()
                .id(2L).username("userB").fullName("用户B").crmSalesNo("10002").build();
        when(userRepository.findByUsername("userA")).thenReturn(Optional.of(userA));
        when(userRepository.findByUsername("userB")).thenReturn(Optional.of(userB));
        mockOssLoginSuccess("oss-token");
        mockGenerateTokenSuccess("crm-jwt-A");
        mockGenerateTokenSuccess("crm-jwt-B");

        // 两个用户都获取 token
        authService.getValidTokenForUser("userA");
        authService.getValidTokenForUser("userB");

        // 用户 A 遇到 401，清除缓存
        authService.handleUnauthorizedForUser("userA");

        // 用户 B 再次获取应仍命中缓存（不重新 generate）
        authService.getValidTokenForUser("userB");

        // generateToken 应被调用 2 次（A 1次 + B 1次），A 的 401 不影响 B
        verify(httpClient, times(2)).postWithAuth(
                anyString(), anyString(), anyString(), anyString());
    }

    // ===== 登出清除 =====

    @Test
    @DisplayName("logoutUser 清除当前用户缓存")
    void logoutUser_clearsUserToken() {
        User user = User.builder()
                .id(1L).username("userA").fullName("用户A").crmSalesNo("10001").build();
        when(userRepository.findByUsername("userA")).thenReturn(Optional.of(user));
        mockOssLoginSuccess("oss-token");
        mockGenerateTokenSequential("crm-jwt-before-logout", "crm-jwt-after-logout");

        authService.getValidTokenForUser("userA");
        authService.logoutUser("userA");
        String token = authService.getValidTokenForUser("userA");

        assertThat(token).isEqualTo("crm-jwt-after-logout");
        verify(httpClient, times(2)).postWithAuth(
                anyString(), anyString(), anyString(), anyString());
    }

    // ===== 用户不存在 =====

    @Test
    @DisplayName("用户不存在 → 回退到全局共享token")
    void getValidTokenForUser_userNotFound_fallsBackToSharedToken() {
        when(userRepository.findByUsername("unknown")).thenReturn(Optional.empty());
        mockOssLoginSuccess("oss-token");
        mockGenerateTokenSuccess("crm-jwt-shared");

        String token = authService.getValidTokenForUser("unknown");

        assertThat(token).isEqualTo("crm-jwt-shared");
    }

    // ===== CO-152 Review D4-1: profile 缓存 =====

    @Test
    @DisplayName("D4-1: 多次调用 getValidTokenForUser 只查一次 DB（profile 缓存命中）")
    void getValidTokenForUser_cachesUserProfile_avoidsRepeatedDbQuery() {
        User user = User.builder()
                .id(1L).username("userA").fullName("用户A").crmSalesNo("10001").build();
        when(userRepository.findByUsername("userA")).thenReturn(Optional.of(user));
        mockOssLoginSuccess("oss-token");
        mockGenerateTokenSuccess("crm-jwt-cached");

        // 第一次调用：DB 查询 + generateToken
        authService.getValidTokenForUser("userA");
        // 第二次调用：token 已缓存，不应再查 DB
        authService.getValidTokenForUser("userA");

        // 关键断言：userRepository.findByUsername 只被调用 1 次（profile 缓存命中）
        verify(userRepository, times(1)).findByUsername("userA");
        // generateToken 也只调 1 次（token 缓存命中）
        verify(httpClient, times(1)).postWithAuth(
                anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("D4-1: logoutUser 后再次调用会重新查 DB（profile 缓存被清除）")
    void getValidTokenForUser_afterLogoutUser_requeriesDb() {
        User user = User.builder()
                .id(1L).username("userA").fullName("用户A").crmSalesNo("10001").build();
        when(userRepository.findByUsername("userA")).thenReturn(Optional.of(user));
        mockOssLoginSuccess("oss-token");
        mockGenerateTokenSequential("crm-jwt-before", "crm-jwt-after");

        authService.getValidTokenForUser("userA");
        authService.logoutUser("userA"); // 清除 profile + token 缓存
        authService.getValidTokenForUser("userA");

        // 关键断言：logoutUser 后 profile 缓存被清，应重新查 DB
        verify(userRepository, times(2)).findByUsername("userA");
    }

    // ===== Helper methods =====

    private void mockOssLoginSuccess(String accessToken) {
        String ossResponse = String.format(
                "{\"code\":0,\"msg\":\"ok\",\"data\":{\"access_token\":\"%s\",\"expires_in\":5998}}",
                accessToken);
        when(httpClient.postForm(anyString(), anyString(), any()))
                .thenReturn(CrmResponseHandler.parse(ossResponse));
    }

    private void mockGenerateTokenSuccess(String crmJwtToken) {
        String response = String.format(
                "{\"code\":0,\"msg\":\"ok\",\"data\":\"%s\"}", crmJwtToken);
        when(httpClient.postWithAuth(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(CrmResponseHandler.parse(response));
    }

    /**
     * 按 body 内容区分返回不同 token：body 包含用户工号 → perUserToken，否则 → sharedToken。
     * 用于同时测试用户专属 token 和全局 fallback token 的场景。
     */
    private void mockGenerateTokenByBody(String perUserToken, String sharedToken) {
        String perUserResponse = String.format(
                "{\"code\":0,\"msg\":\"ok\",\"data\":\"%s\"}", perUserToken);
        String sharedResponse = String.format(
                "{\"code\":0,\"msg\":\"ok\",\"data\":\"%s\"}", sharedToken);
        when(httpClient.postWithAuth(anyString(), anyString(), anyString(), anyString()))
                .thenAnswer(inv -> {
                    String body = inv.getArgument(3);
                    // 全局共享路径用 properties 里的 salesNo（03595），用户路径用用户的 crmSalesNo
                    String globalSalesNo = properties.getGenerateTokenSalesNo();
                    if (body != null && body.contains(globalSalesNo)) {
                        return CrmResponseHandler.parse(sharedResponse);
                    }
                    return CrmResponseHandler.parse(perUserResponse);
                });
    }

    /**
     * 按调用顺序依次返回不同 token（用于测试缓存失效后重新 generate）。
     */
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
