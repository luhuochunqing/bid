package com.xiyu.bid.crm.application;

import com.xiyu.bid.crm.config.CrmProperties;
import com.xiyu.bid.crm.infrastructure.CrmHttpClient;
import com.xiyu.bid.crm.infrastructure.CrmResponseHandler;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.exception.ExternalServiceException;
import com.xiyu.bid.exception.RoleNotAuthorizedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

/**
 * OSS 委托及直接登录失败分支测试：区分凭据错误、系统未授权、OSS 不可达。
 * <p>
 * 使用手写桩而非 Mockito，保证在不允许 JVM self-attach 的受限沙箱中也可执行。
 */
class OssDelegationFailureTest {

    private CrmProperties properties;
    private StubCrmHttpClient crmHttpClient;
    private OssDelegationService service;
    private OssLoginFlowService loginFlowService;

    @BeforeEach
    void setUp() {
        properties = new CrmProperties();
        CrmProperties.CrmAuthPaths authPaths = new CrmProperties.CrmAuthPaths();
        authPaths.setOauthLoginPath("/oauth/login");
        properties.setAuth(authPaths);
        properties.setAuthBaseUrl("https://base-oss.ehsy.com");

        crmHttpClient = new StubCrmHttpClient();
        service = new OssDelegationService(crmHttpClient, properties);
        // 失败响应必须在登录后流程之前返回，不应访问权限、角色或缓存协作对象。
        loginFlowService = new OssLoginFlowService(crmHttpClient, properties, null, null, null, null, null);
    }

    @ParameterizedTest(name = "directLogin={0}")
    @ValueSource(booleans = {false, true})
    void authenticate_shouldReturnFalseWhenOssRejectsPassword(boolean directLogin) {
        crmHttpClient.response = new CrmResponseHandler.CrmApiResponse(501, "登录名或密码错误", null, false);

        assertThat(authenticate(directLogin)).isFalse();
    }

    @ParameterizedTest(name = "directLogin={0}")
    @ValueSource(booleans = {false, true})
    void authenticate_shouldThrowRoleNotAuthorizedWhenOssDeniesSystemAccess(boolean directLogin) {
        crmHttpClient.response = new CrmResponseHandler.CrmApiResponse(501, "没有权限访问该系统", null, false);

        assertThatThrownBy(() -> authenticate(directLogin))
                .isInstanceOf(RoleNotAuthorizedException.class)
                .hasMessageContaining("没有权限访问该系统");
    }

    @ParameterizedTest(name = "directLogin={0}")
    @ValueSource(booleans = {false, true})
    void authenticate_shouldThrowExternalServiceExceptionWhenOssUnreachable(boolean directLogin) {
        crmHttpClient.response = CrmResponseHandler.CrmApiResponse.parseError("I/O error on POST request");

        assertThatThrownBy(() -> authenticate(directLogin))
                .isInstanceOf(ExternalServiceException.class)
                .hasMessageContaining("OSS 统一认证服务暂时不可用");
    }

    @ParameterizedTest(name = "directLogin={0}")
    @ValueSource(booleans = {false, true})
    void authenticate_shouldTreatEmbeddedCredentialErrorAsCredential(boolean directLogin) {
        // 兼容错误详情内嵌凭据错误文本的响应（code=-1），不能误判为服务不可用。
        crmHttpClient.response = CrmResponseHandler.CrmApiResponse.parseError(
                "500 : \"{\\\"code\\\":501,\\\"message\\\":\\\"登录名或密码错误\\\",\\\"success\\\":false}\"");

        assertThat(authenticate(directLogin)).isFalse();
    }

    @ParameterizedTest
    @CsvSource({"false, 500", "true, 500", "false, 503", "true, 503"})
    void authenticate_shouldReportHttpServiceFailure(boolean directLogin, int status) {
        MockRestServiceServer server = useHttpResponse(status,
                "{\"code\":" + status + ",\"message\":\"服务不可用\",\"success\":false}");

        assertThatThrownBy(() -> authenticate(directLogin))
                .isInstanceOf(ExternalServiceException.class)
                .hasMessageContaining("OSS 统一认证服务暂时不可用");
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void authenticate_shouldKeepHttpPermissionDenial(boolean directLogin) {
        MockRestServiceServer server = useHttpResponse(500,
                "{\"code\":501,\"message\":\"没有权限访问该系统\",\"success\":false}");

        assertThatThrownBy(() -> authenticate(directLogin))
                .isInstanceOf(RoleNotAuthorizedException.class)
                .hasMessageContaining("没有权限访问该系统");
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void authenticate_shouldKeepHttpCredentialRejection(boolean directLogin) {
        MockRestServiceServer server = useHttpResponse(500,
                "{\"code\":501,\"message\":\"登录名或密码错误\",\"success\":false}");

        assertThat(authenticate(directLogin)).isFalse();
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void authenticate_shouldRejectSuccessBodyOnHttpFailure(boolean directLogin) {
        MockRestServiceServer server = useHttpResponse(503,
                "{\"code\":0,\"message\":\"ok\",\"success\":true,\"data\":{}}");

        assertThatThrownBy(() -> authenticate(directLogin))
                .isInstanceOf(ExternalServiceException.class);
        server.verify();
    }

    private MockRestServiceServer useHttpResponse(int status, String body) {
        CrmHttpClient client = new CrmHttpClient(properties);
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(client, "restTemplate");
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("https://base-oss.ehsy.com/oauth/login"))
                .andRespond(withStatus(HttpStatus.valueOf(status)).contentType(MediaType.APPLICATION_JSON).body(body));
        service = new OssDelegationService(client, properties);
        loginFlowService = new OssLoginFlowService(client, properties, null, null, null, null, null);
        return server;
    }

    private boolean authenticate(boolean directLogin) {
        return directLogin
                ? loginFlowService.authenticateDirect("06234", "123456").isAuthenticated()
                : service.authenticate(User.builder().username("06234").build(), "123456");
    }

    private static final class StubCrmHttpClient extends CrmHttpClient {

        private CrmResponseHandler.CrmApiResponse response;

        private StubCrmHttpClient() {
            super(null);
        }

        @Override
        public CrmResponseHandler.CrmApiResponse postForm(
                String baseUrl, String path, MultiValueMap<String, String> formData) {
            return response;
        }
    }
}
