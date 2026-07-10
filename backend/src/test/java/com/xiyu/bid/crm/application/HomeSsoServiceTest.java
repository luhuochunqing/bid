package com.xiyu.bid.crm.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xiyu.bid.dto.AuthSessionResult;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("HomeSsoService - Home 平台 SSO 免登业务服务（调用 OSS 实时接口）")
@ExtendWith(MockitoExtension.class)
class HomeSsoServiceTest {

    private static final String VALID_TOKEN = "valid-token-123";
    private static final String INVALID_TOKEN = "invalid-token-456";
    private static final String USERNAME = "09118";

    @Mock
    private OssLoginFlowService ossLoginFlowService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuthService authService;

    @Mock
    private OssUserAutoCreator ossUserAutoCreator;

    @InjectMocks
    private HomeSsoService homeSsoService;

    private User localUser;
    private User autoCreatedUser;
    private AuthSessionResult sessionResult;
    private OssLoginResult successResult;
    private OssLoginResult failedResult;

    @BeforeEach
    void setUp() {
        localUser = User.builder()
                .id(1L)
                .username(USERNAME)
                .email("test@example.com")
                .fullName("测试用户")
                .enabled(false) // 本地 enabled=false，但 SSO 不应检查此字段
                .build();

        autoCreatedUser = User.builder()
                .id(2L)
                .username(USERNAME)
                .email(USERNAME + "@oss-login.local")
                .fullName(USERNAME)
                .enabled(true)
                .externalOrgSourceApp(OssUserAutoCreator.AUTO_CREATE_SOURCE_APP)
                .build();

        sessionResult = AuthSessionResult.builder()
                .accessToken("access-token")
                .refreshToken("refresh-token")
                .build();

        ObjectMapper mapper = new ObjectMapper();
        ObjectNode employeeInfo = mapper.createObjectNode();
        employeeInfo.put("username", USERNAME);
        employeeInfo.put("nickName", "测试用户");
        employeeInfo.put("status", 1);

        successResult = OssLoginResult.builder()
                .username(USERNAME)
                .authenticated(true)
                .ossAccessToken(VALID_TOKEN)
                .employeeInfo(employeeInfo)
                .build();

        failedResult = OssLoginResult.builder()
                .authenticated(false)
                .build();
    }

    @Test
    @DisplayName("OSS 实时校验成功且本地有用户记录时返回登录结果（即使本地 enabled=false）")
    void ssoLogin_ossAuthenticatedAndLocalUserExists_returnsSessionResult() {
        when(ossLoginFlowService.authenticateWithExistingToken(VALID_TOKEN)).thenReturn(successResult);
        when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.of(localUser));
        when(authService.loginWithoutPassword(localUser)).thenReturn(sessionResult);

        AuthSessionResult result = homeSsoService.ssoLogin(VALID_TOKEN);

        assertThat(result).isNotNull();
        assertThat(result.getAccessToken()).isEqualTo("access-token");
        assertThat(result.getRefreshToken()).isEqualTo("refresh-token");
        verify(ossLoginFlowService).authenticateWithExistingToken(VALID_TOKEN);
        verify(userRepository).findByUsername(USERNAME);
        verify(authService).loginWithoutPassword(localUser);
        verify(ossUserAutoCreator, never()).autoCreateIfAbsent(any());
    }

    @Test
    @DisplayName("OSS 实时校验失败时抛出异常")
    void ssoLogin_ossAuthenticationFailed_throwsException() {
        when(ossLoginFlowService.authenticateWithExistingToken(INVALID_TOKEN)).thenReturn(failedResult);

        assertThatThrownBy(() -> homeSsoService.ssoLogin(INVALID_TOKEN))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("SSO token 无效或已过期");

        verify(ossLoginFlowService).authenticateWithExistingToken(INVALID_TOKEN);
        verify(userRepository, never()).findByUsername(anyString());
        verify(authService, never()).loginWithoutPassword(any(User.class));
    }

    @Test
    @DisplayName("OSS 返回空用户名时抛出异常")
    void ssoLogin_ossReturnsEmptyUsername_throwsException() {
        OssLoginResult emptyUsernameResult = OssLoginResult.builder()
                .authenticated(true)
                .username("")
                .build();
        when(ossLoginFlowService.authenticateWithExistingToken(VALID_TOKEN)).thenReturn(emptyUsernameResult);

        assertThatThrownBy(() -> homeSsoService.ssoLogin(VALID_TOKEN))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("无法获取用户信息");

        verify(ossLoginFlowService).authenticateWithExistingToken(VALID_TOKEN);
        verify(userRepository, never()).findByUsername(anyString());
        verify(authService, never()).loginWithoutPassword(any(User.class));
    }

    /**
     * 根因行为测试：OSS 鉴权成功 + 本地无用户记录 → 自动创建本地 User 并允许登录。
     * <p>
     * 根因修复前：抛 BadCredentialsException("本地无此用户记录")，违反"OSS 实时鉴权是唯一真相源"设计意图。
     * 根因修复后：调用 OssUserAutoCreator.autoCreateIfAbsent 创建本地记录，继续登录流程。
     * <p>
     * 对应 7 大根因中的根因 1（追症状不追根因）+ 根因 6（对系统设计意图的理解偏差）。
     */
    @Test
    @DisplayName("根因修复：OSS 鉴权成功 + 本地无记录 → 自动创建 User 并登录成功")
    void ssoLogin_ossAuthSucceededButLocalNotFound_autoCreatesUserAndLogsIn() {
        when(ossLoginFlowService.authenticateWithExistingToken(VALID_TOKEN)).thenReturn(successResult);
        when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.empty());
        when(ossUserAutoCreator.autoCreateIfAbsent(successResult)).thenReturn(autoCreatedUser);
        when(authService.loginWithoutPassword(autoCreatedUser)).thenReturn(sessionResult);

        AuthSessionResult result = homeSsoService.ssoLogin(VALID_TOKEN);

        assertThat(result).isNotNull();
        assertThat(result.getAccessToken()).isEqualTo("access-token");
        // 根因行为验证：本地无记录时调用了 autoCreateIfAbsent
        verify(ossUserAutoCreator).autoCreateIfAbsent(successResult);
        verify(authService).loginWithoutPassword(autoCreatedUser);
        // 不应抛"本地无此用户记录"异常（根因修复点）
        verify(ossLoginFlowService).authenticateWithExistingToken(VALID_TOKEN);
    }
}
