package com.xiyu.bid.integration.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestToUriTemplate;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Unit tests for WeComApiClient using MockRestServiceServer.
 */
@DisplayName("WeComApiClient — HTTP calls to WeCom API")
class WeComApiClientTest {

    private static final String BASE_URL = "http://test-wecom.local";

    private MockRestServiceServer server;
    private WeComApiClient client;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.createServer(restTemplate);
        // Build client with a builder that wraps our instrumented RestTemplate
        RestTemplateBuilder builder = new RestTemplateBuilder()
                .additionalInterceptors((request, body, execution) -> execution.execute(request, body));
        // Override: directly inject pre-built RestTemplate via reflection-free approach
        client = new WeComApiClientTestHelper(restTemplate, BASE_URL);
    }

    @Test
    @DisplayName("requestAccessToken success → returns token and expiresIn")
    void requestAccessToken_success() {
        server.expect(requestToUriTemplate(BASE_URL + "/cgi-bin/gettoken?corpid={a}&corpsecret={b}",
                        "corp123", "secret456"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"access_token\":\"TOKEN_ABC\",\"expires_in\":7200,\"errcode\":0,\"errmsg\":\"ok\"}",
                        MediaType.APPLICATION_JSON));

        WeComApiClient.WeComAccessTokenResponse response = client.requestAccessToken("corp123", "secret456");

        assertThat(response.errcode()).isEqualTo(0);
        assertThat(response.token()).isEqualTo("TOKEN_ABC");
        assertThat(response.expiresIn()).isEqualTo(7200L);
        server.verify();
    }

    @Test
    @DisplayName("requestAccessToken errcode=40001 → returns non-zero errcode")
    void requestAccessToken_invalidCredential_returnsErrcode() {
        server.expect(requestToUriTemplate(BASE_URL + "/cgi-bin/gettoken?corpid={a}&corpsecret={b}",
                        "corp123", "wrong"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"errcode\":40001,\"errmsg\":\"invalid credential\"}",
                        MediaType.APPLICATION_JSON));

        WeComApiClient.WeComAccessTokenResponse response = client.requestAccessToken("corp123", "wrong");

        assertThat(response.errcode()).isEqualTo(40001);
        assertThat(response.token()).isNull();
        server.verify();
    }

    @Test
    @DisplayName("requestAccessToken 500 → throws WeComApiException with errcode=UNKNOWN")
    void requestAccessToken_serverError_throwsWeComApiException() {
        server.expect(requestToUriTemplate(BASE_URL + "/cgi-bin/gettoken?corpid={a}&corpsecret={b}",
                        "corp123", "secret"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.requestAccessToken("corp123", "secret"))
                .isInstanceOf(WeComApiException.class)
                .satisfies(ex -> assertThat(((WeComApiException) ex).errcode()).isEqualTo(-1));
        server.verify();
    }

    @Test
    @DisplayName("sendAppMessage success → returns errcode=0")
    void sendAppMessage_success() {
        server.expect(requestToUriTemplate(BASE_URL + "/cgi-bin/message/send?access_token={t}", "mytoken"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(
                        "{\"errcode\":0,\"errmsg\":\"ok\"}",
                        MediaType.APPLICATION_JSON));

        WeComApiClient.WeComSendResponse response = client.sendAppMessage("mytoken", java.util.Map.of("touser", "u1"));

        assertThat(response.errcode()).isEqualTo(0);
        server.verify();
    }

    @Test
    @DisplayName("sendAppMessage errcode=40014 → returns 40014")
    void sendAppMessage_invalidToken_returns40014() {
        server.expect(requestToUriTemplate(BASE_URL + "/cgi-bin/message/send?access_token={t}", "badtoken"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(
                        "{\"errcode\":40014,\"errmsg\":\"invalid access_token\"}",
                        MediaType.APPLICATION_JSON));

        WeComApiClient.WeComSendResponse response = client.sendAppMessage("badtoken", java.util.Map.of());

        assertThat(response.errcode()).isEqualTo(40014);
        server.verify();
    }

    @Test
    @DisplayName("requestUserInfo success → returns UserId and OpenId")
    void requestUserInfo_success() {
        server.expect(requestToUriTemplate(BASE_URL + "/cgi-bin/user/getuserinfo?access_token={t}&code={c}",
                        "mytoken", "mycode"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"errcode\":0,\"errmsg\":\"ok\",\"UserId\":\"U123\",\"OpenId\":\"O123\",\"user_ticket\":\"T123\",\"expires_in\":7200}",
                        MediaType.APPLICATION_JSON));

        WeComApiClient.WeComUserInfoResponse response = client.requestUserInfo("mytoken", "mycode");

        assertThat(response.errcode()).isEqualTo(0);
        assertThat(response.userId()).isEqualTo("U123");
        assertThat(response.user_ticket()).isEqualTo("T123");
        server.verify();
    }

    @Test
    @DisplayName("requestUserDetail success → returns mobile and email")
    void requestUserDetail_success() {
        server.expect(requestToUriTemplate(BASE_URL + "/cgi-bin/user/getuserdetail?access_token={t}", "mytoken"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(
                        "{\"errcode\":0,\"errmsg\":\"ok\",\"userid\":\"U123\",\"name\":\"Name\",\"mobile\":\"13800138000\"}",
                        MediaType.APPLICATION_JSON));

        WeComApiClient.WeComUserDetailResponse response = client.requestUserDetail("mytoken", "T123");

        assertThat(response.errcode()).isEqualTo(0);
        assertThat(response.userid()).isEqualTo("U123");
        assertThat(response.mobile()).isEqualTo("13800138000");
        server.verify();
    }

    /**
     * Test-only subclass that accepts a pre-built RestTemplate so we can wrap it
     * with MockRestServiceServer without needing Spring context.
     */
    static class WeComApiClientTestHelper extends WeComApiClient {

        WeComApiClientTestHelper(RestTemplate restTemplate, String baseUrl) {
            super(new RestTemplateBuilder(), baseUrl, 1000, 1000);
            // Replace the restTemplate field via the package-private constructor path
            // Actually we need a different approach — use the overridable constructor
            injectRestTemplate(restTemplate, baseUrl);
        }

        private void injectRestTemplate(RestTemplate restTemplate, String baseUrl) {
            // Use reflection to inject the mock-server-backed RestTemplate
            try {
                var field = WeComApiClient.class.getDeclaredField("restTemplate");
                field.setAccessible(true);
                field.set(this, restTemplate);
                var baseUrlField = WeComApiClient.class.getDeclaredField("baseUrl");
                baseUrlField.setAccessible(true);
                baseUrlField.set(this, baseUrl);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}
