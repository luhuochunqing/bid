package com.xiyu.bid.integration.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.crm.application.OssLoginFlowService;
import com.xiyu.bid.crm.application.OssLoginResult;
import com.xiyu.bid.crm.application.OssUserAutoCreator;
import com.xiyu.bid.crm.config.CrmProperties;
import com.xiyu.bid.crm.infrastructure.CrmHttpClient;
import com.xiyu.bid.crm.infrastructure.CrmResponseHandler;
import com.xiyu.bid.dto.AuthSessionResult;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.integration.infrastructure.persistence.entity.WeComIntegrationEntity;
import com.xiyu.bid.integration.infrastructure.persistence.repository.WeComIntegrationJpaRepository;
import com.xiyu.bid.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * WeComSsoOssLoginService 单元测试。
 * <p>
 * 验证企微 SSO 通过 base-oss /qyWeixin/loginQywx 换 OSS token 的完整流程。
 * 不依赖 Spring Context，纯 Mockito mock。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WeComSsoOssLoginService — 企微 SSO via base-oss")
class WeComSsoOssLoginServiceTest {

    private static final String OSS_BASE_URL = "https://base-oss-test.ehsy.com";
    private static final String QYWX_LOGIN_PATH = "/qyWeixin/loginQywx";
    private static final String AGENT_ID = "1000002";
    private static final String OSS_TOKEN = "oss-access-token-from-base-oss";
    private static final String WECOM_CODE = "wecom-oauth-code-xxx";

    @Mock
    private CrmHttpClient crmHttpClient;
    @Mock
    private CrmProperties crmProperties;
    @Mock
    private OssLoginFlowService ossLoginFlowService;
    @Mock
    private OssUserAutoCreator ossUserAutoCreator;
    @Mock
    private AuthService authService;
    @Mock
    private WeComIntegrationJpaRepository integrationRepository;

    private WeComSsoOssLoginService service;

    @BeforeEach
    void setUp() {
        service = new WeComSsoOssLoginService(
                crmHttpClient, crmProperties, ossLoginFlowService,
                ossUserAutoCreator, authService, integrationRepository);
    }

    @Test
    @DisplayName("loginByWeComCode: 正常流程 → 换 token → OSS 流程 → 自动建用户 → 返回 AuthSessionResult")
    void loginByWeComCode_success() {
        // Arrange
        setupCrmProperties();
        setupIntegrationEntity(true, AGENT_ID);
        setupQywxLoginResponse(OSS_TOKEN, 7200);

        OssLoginResult ossResult = OssLoginResult.builder()
                .authenticated(true)
                .ossAccessToken(OSS_TOKEN)
                .username("testuser")
                .build();
        when(ossLoginFlowService.authenticateWithExistingToken(OSS_TOKEN)).thenReturn(ossResult);

        User user = new User();
        user.setUsername("testuser");
        when(ossUserAutoCreator.autoCreateIfAbsent(ossResult)).thenReturn(user);

        AuthSessionResult expected = mock(AuthSessionResult.class);
        when(authService.loginWithoutPassword(user)).thenReturn(expected);

        // Act
        Optional<AuthSessionResult> result = service.loginByWeComCode(WECOM_CODE);

        // Assert
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(expected);

        verify(crmHttpClient).getQywxLogin(eq(OSS_BASE_URL), eq(QYWX_LOGIN_PATH),
                eq(WECOM_CODE), eq(AGENT_ID));
        verify(ossLoginFlowService).authenticateWithExistingToken(OSS_TOKEN);
        verify(ossUserAutoCreator).autoCreateIfAbsent(ossResult);
        verify(authService).loginWithoutPassword(user);
    }

    @Test
    @DisplayName("loginByWeComCode: WeCom 集成未配置 → 返回 empty")
    void loginByWeComCode_integrationNotConfigured() {
        when(integrationRepository.findById(1L)).thenReturn(Optional.empty());

        Optional<AuthSessionResult> result = service.loginByWeComCode(WECOM_CODE);

        assertThat(result).isEmpty();
        verify(crmHttpClient, never()).getQywxLogin(anyString(), anyString(), anyString(), anyString());
        verify(ossLoginFlowService, never()).authenticateWithExistingToken(anyString());
    }

    @Test
    @DisplayName("loginByWeComCode: SSO 未启用 → 返回 empty")
    void loginByWeComCode_ssoDisabled() {
        setupIntegrationEntity(false, AGENT_ID);

        Optional<AuthSessionResult> result = service.loginByWeComCode(WECOM_CODE);

        assertThat(result).isEmpty();
        verify(crmHttpClient, never()).getQywxLogin(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("loginByWeComCode: base-oss 返回失败 → 返回 empty")
    void loginByWeComCode_ossExchangeFailed() {
        setupCrmProperties();
        setupIntegrationEntity(true, AGENT_ID);
        // base-oss 返回失败响应
        CrmResponseHandler.CrmApiResponse failed =
                new CrmResponseHandler.CrmApiResponse(500, "internal error", null, false);
        when(crmHttpClient.getQywxLogin(OSS_BASE_URL, QYWX_LOGIN_PATH, WECOM_CODE, AGENT_ID))
                .thenReturn(failed);

        Optional<AuthSessionResult> result = service.loginByWeComCode(WECOM_CODE);

        assertThat(result).isEmpty();
        verify(ossLoginFlowService, never()).authenticateWithExistingToken(anyString());
    }

    @Test
    @DisplayName("loginByWeComCode: OSS token 为空字符串 → 返回 empty")
    void loginByWeComCode_emptyToken() {
        setupCrmProperties();
        setupIntegrationEntity(true, AGENT_ID);
        setupQywxLoginResponse("", 0);

        Optional<AuthSessionResult> result = service.loginByWeComCode(WECOM_CODE);

        assertThat(result).isEmpty();
        verify(ossLoginFlowService, never()).authenticateWithExistingToken(anyString());
    }

    @Test
    @DisplayName("loginByWeComCode: authenticateWithExistingToken 返回 authenticated=false → 返回 empty")
    void loginByWeComCode_ossAuthFailed() {
        setupCrmProperties();
        setupIntegrationEntity(true, AGENT_ID);
        setupQywxLoginResponse(OSS_TOKEN, 7200);

        OssLoginResult failedResult = OssLoginResult.builder()
                .authenticated(false)
                .ossAccessToken(OSS_TOKEN)
                .build();
        when(ossLoginFlowService.authenticateWithExistingToken(OSS_TOKEN)).thenReturn(failedResult);

        Optional<AuthSessionResult> result = service.loginByWeComCode(WECOM_CODE);

        assertThat(result).isEmpty();
        verify(ossUserAutoCreator, never()).autoCreateIfAbsent(any());
        verify(authService, never()).loginWithoutPassword(any());
    }

    @Test
    @DisplayName("loginByWeComCode: agentId 为空 → 返回 empty")
    void loginByWeComCode_emptyAgentId() {
        setupIntegrationEntity(true, "");

        Optional<AuthSessionResult> result = service.loginByWeComCode(WECOM_CODE);

        assertThat(result).isEmpty();
        verify(crmHttpClient, never()).getQywxLogin(anyString(), anyString(), anyString(), anyString());
    }

    /**
     * Helper: 模拟 CrmProperties 返回 base-url 和 qywxLoginPath.
     */
    private void setupCrmProperties() {
        when(crmProperties.getEffectiveAuthBaseUrl()).thenReturn(OSS_BASE_URL);
        CrmProperties.CrmAuthPaths authPaths = new CrmProperties.CrmAuthPaths();
        authPaths.setQywxLoginPath(QYWX_LOGIN_PATH);
        when(crmProperties.getAuth()).thenReturn(authPaths);
    }

    /**
     * Helper: 模拟 WeComIntegrationEntity 返回.
     */
    private void setupIntegrationEntity(boolean ssoEnabled, String agentId) {
        WeComIntegrationEntity entity = new WeComIntegrationEntity();
        entity.setCorpId("ww_test_corp_id");
        entity.setAgentId(agentId);
        entity.setSsoEnabled(ssoEnabled);
        when(integrationRepository.findById(1L)).thenReturn(Optional.of(entity));
    }

    /**
     * Helper: 模拟 base-oss /qyWeixin/loginQywx 接口返回 access_token.
     */
    private void setupQywxLoginResponse(String token, long expiresIn) {
        try {
            String body = String.format(
                    "{\"code\":0,\"msg\":\"ok\",\"data\":{\"access_token\":\"%s\",\"expires_in\":%d}}",
                    token, expiresIn);
            JsonNode data = new ObjectMapper().readTree(body).path("data");
            CrmResponseHandler.CrmApiResponse response =
                    new CrmResponseHandler.CrmApiResponse(0, "ok", data, true);
            when(crmHttpClient.getQywxLogin(OSS_BASE_URL, QYWX_LOGIN_PATH, WECOM_CODE, AGENT_ID))
                    .thenReturn(response);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
