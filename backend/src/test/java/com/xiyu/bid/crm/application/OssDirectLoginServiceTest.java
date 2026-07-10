package com.xiyu.bid.crm.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xiyu.bid.dto.LoginRequest;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.exception.RoleNotAuthorizedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OssDirectLoginService 单元测试。
 * <p>
 * 覆盖两个核心方法：
 * <ol>
 *   <li>{@link OssDirectLoginService#tryDirectLogin} — OSS 直接登录编排</li>
 *   <li>{@link OssDirectLoginService#requireOssRole} — 白名单权限校验</li>
 * </ol>
 * <p>
 * 关键安全断言：tryDirectLogin 在 autoCreateIfAbsent 之后必须调用 requireOssRole，
 * 防止 roleCode 不在白名单的 OSS 用户绕过权限检查获取有效 JWT。
 */
@DisplayName("OssDirectLoginService - OSS 直接登录 + 白名单校验")
@ExtendWith(MockitoExtension.class)
class OssDirectLoginServiceTest {

    private static final String USERNAME = "06669";
    private static final String PASSWORD = "Ehsy1234@";
    private static final String VALID_ROLE_CODE = "bid-Team";
    private static final String INVALID_ROLE_CODE = "unknown-role";

    @Mock
    private OssLoginFlowService ossLoginFlowService;

    @Mock
    private OssUserAutoCreator ossUserAutoCreator;

    @Mock
    private OssPermissionCache ossPermissionCache;

    @InjectMocks
    private OssDirectLoginService ossDirectLoginService;

    private LoginRequest loginRequest;
    private OssLoginResult successResult;
    private OssLoginResult failedResult;
    private User ossUser;
    private User localUser;

    @BeforeEach
    void setUp() {
        loginRequest = new LoginRequest();
        loginRequest.setUsername(USERNAME);
        loginRequest.setPassword(PASSWORD);

        ObjectMapper mapper = new ObjectMapper();
        ObjectNode employeeInfo = mapper.createObjectNode();
        employeeInfo.put("name", "测试用户");

        successResult = OssLoginResult.builder()
                .username(USERNAME)
                .authenticated(true)
                .ossAccessToken("oss-token-123")
                .employeeInfo(employeeInfo)
                .build();

        failedResult = OssLoginResult.builder()
                .authenticated(false)
                .build();

        ossUser = User.builder()
                .id(1L)
                .username(USERNAME)
                .externalOrgSourceApp(OssUserAutoCreator.AUTO_CREATE_SOURCE_APP)
                .build();

        localUser = User.builder()
                .id(2L)
                .username("local-admin")
                .externalOrgSourceApp(null)  // 本地用户，无 externalOrgSourceApp
                .build();
    }

    // ==================== tryDirectLogin 测试 ====================

    @Test
    @DisplayName("OSS 鉴权成功 + 白名单校验通过 → 返回自动创建的 User")
    void tryDirectLogin_ossAuthSucceededAndRoleAllowed_returnsUser() {
        when(ossLoginFlowService.authenticateDirect(USERNAME, PASSWORD)).thenReturn(successResult);
        when(ossUserAutoCreator.autoCreateIfAbsent(successResult)).thenReturn(ossUser);
        when(ossPermissionCache.getRoleCode(USERNAME)).thenReturn(Optional.of(VALID_ROLE_CODE));

        Optional<User> result = ossDirectLoginService.tryDirectLogin(loginRequest);

        assertThat(result).isPresent();
        assertThat(result.get().getUsername()).isEqualTo(USERNAME);
        verify(ossLoginFlowService).authenticateDirect(USERNAME, PASSWORD);
        verify(ossUserAutoCreator).autoCreateIfAbsent(successResult);
        verify(ossPermissionCache).getRoleCode(USERNAME);
    }

    @Test
    @DisplayName("OSS 鉴权失败 → 返回 empty，不自动创建用户")
    void tryDirectLogin_ossAuthFailed_returnsEmpty() {
        when(ossLoginFlowService.authenticateDirect(USERNAME, PASSWORD)).thenReturn(failedResult);

        Optional<User> result = ossDirectLoginService.tryDirectLogin(loginRequest);

        assertThat(result).isEmpty();
        verify(ossLoginFlowService).authenticateDirect(USERNAME, PASSWORD);
        verify(ossUserAutoCreator, never()).autoCreateIfAbsent(any());
        verify(ossPermissionCache, never()).getRoleCode(anyString());
    }

    /**
     * CRITICAL-1 回归测试：OSS 鉴权成功但 roleCode 不在白名单 → 抛 RoleNotAuthorizedException。
     * <p>
     * 修复前：tryDirectLogin 不调用 requireOssRole，roleCode 不在白名单的 OSS 用户
     * 也能绕过权限检查获取有效 JWT。
     * 修复后：autoCreateIfAbsent 之后调用 requireOssRole，fail-closed。
     */
    @Test
    @DisplayName("CRITICAL-1 回归：OSS 鉴权成功 + roleCode 不在白名单 → 抛 RoleNotAuthorizedException")
    void tryDirectLogin_ossAuthSucceededButRoleNotInWhitelist_throwsRoleNotAuthorized() {
        when(ossLoginFlowService.authenticateDirect(USERNAME, PASSWORD)).thenReturn(successResult);
        when(ossUserAutoCreator.autoCreateIfAbsent(successResult)).thenReturn(ossUser);
        when(ossPermissionCache.getRoleCode(USERNAME)).thenReturn(Optional.of(INVALID_ROLE_CODE));

        assertThatThrownBy(() -> ossDirectLoginService.tryDirectLogin(loginRequest))
                .isInstanceOf(RoleNotAuthorizedException.class)
                .hasMessageContaining("角色未授权");

        // 验证：即使白名单拒绝，autoCreateIfAbsent 仍被调用（本地记录已创建）
        // 但用户无法获取 JWT（tryDirectLogin 抛异常，AuthService.login 不会调 buildSession）
        verify(ossUserAutoCreator).autoCreateIfAbsent(successResult);
    }

    /**
     * CRITICAL-1 回归测试：OSS 鉴权成功但缓存 roleCode 为空 → 抛 RoleNotAuthorizedException。
     */
    @Test
    @DisplayName("CRITICAL-1 回归：OSS 鉴权成功 + 缓存无 roleCode → 抛 RoleNotAuthorizedException")
    void tryDirectLogin_ossAuthSucceededButNoCachedRole_throwsRoleNotAuthorized() {
        when(ossLoginFlowService.authenticateDirect(USERNAME, PASSWORD)).thenReturn(successResult);
        when(ossUserAutoCreator.autoCreateIfAbsent(successResult)).thenReturn(ossUser);
        when(ossPermissionCache.getRoleCode(USERNAME)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ossDirectLoginService.tryDirectLogin(loginRequest))
                .isInstanceOf(RoleNotAuthorizedException.class)
                .hasMessageContaining("无有效 OSS 角色");
    }

    // ==================== requireOssRole 测试 ====================

    @Test
    @DisplayName("本地用户（非 OSS）→ 短路放行，不检查缓存")
    void requireOssRole_localUser_shortCircuits() {
        ossDirectLoginService.requireOssRole(localUser);

        verify(ossPermissionCache, never()).getRoleCode(anyString());
    }

    @Test
    @DisplayName("OSS 用户 + 缓存有白名单角色 → 放行")
    void requireOssRole_ossUserWithAllowedRole_passes() {
        when(ossPermissionCache.getRoleCode(USERNAME)).thenReturn(Optional.of(VALID_ROLE_CODE));

        ossDirectLoginService.requireOssRole(ossUser);

        verify(ossPermissionCache).getRoleCode(USERNAME);
    }

    @Test
    @DisplayName("OSS 用户 + 缓存无 roleCode → 抛异常")
    void requireOssRole_ossUserWithNoCachedRole_throws() {
        when(ossPermissionCache.getRoleCode(USERNAME)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ossDirectLoginService.requireOssRole(ossUser))
                .isInstanceOf(RoleNotAuthorizedException.class)
                .hasMessageContaining("无有效 OSS 角色");
    }

    @Test
    @DisplayName("OSS 用户 + 缓存 roleCode 为空白 → 抛异常")
    void requireOssRole_ossUserWithBlankCachedRole_throws() {
        when(ossPermissionCache.getRoleCode(USERNAME)).thenReturn(Optional.of(""));

        assertThatThrownBy(() -> ossDirectLoginService.requireOssRole(ossUser))
                .isInstanceOf(RoleNotAuthorizedException.class)
                .hasMessageContaining("无有效 OSS 角色");
    }

    @Test
    @DisplayName("OSS 用户 + roleCode 不在白名单 → 抛异常")
    void requireOssRole_ossUserWithRoleNotInWhitelist_throws() {
        when(ossPermissionCache.getRoleCode(USERNAME)).thenReturn(Optional.of(INVALID_ROLE_CODE));

        assertThatThrownBy(() -> ossDirectLoginService.requireOssRole(ossUser))
                .isInstanceOf(RoleNotAuthorizedException.class)
                .hasMessageContaining("角色未授权");
    }
}
