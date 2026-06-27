package com.xiyu.bid.crm.infrastructure;

import com.xiyu.bid.crm.config.CrmProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@DisplayName("HomeSsoClient - Home 平台 SSO token 校验客户端")
class HomeSsoClientTest {

    private static final String BASE_URL = "https://home.example.test";
    private static final String VALID_TOKEN = "valid-token-123";
    private static final String INVALID_TOKEN = "invalid-token-456";
    private static final String USERNAME = "09118";

    private CrmProperties properties;

    @BeforeEach
    void setUp() {
        properties = new CrmProperties();
        properties.setAuthBaseUrl(BASE_URL);
    }

    private String buildUrl() {
        return BASE_URL + "/oauth/getUserInfo";
    }

    private String validTokenResponse(String username) {
        return """
                {
                  "code": 0,
                  "message": "success",
                  "data": {
                    "username": "%s",
                    "nickName": "测试用户",
                    "status": 1
                  }
                }
                """.formatted(username);
    }

    private String invalidTokenResponse() {
        return """
                {
                  "code": 401,
                  "message": "token invalid or expired",
                  "data": null
                }
                """;
    }

    @Test
    @DisplayName("token 有效时返回用户名")
    void validateTokenAndGetUsername_validToken_returnsUsername() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo(buildUrl()))
                .andRespond(withSuccess(validTokenResponse(USERNAME), MediaType.APPLICATION_JSON));

        HomeSsoClient client = new HomeSsoClient(new TestCrmHttpClient(restTemplate, properties), properties);
        Optional<String> result = client.validateTokenAndGetUsername(VALID_TOKEN);

        assertThat(result).isPresent().contains(USERNAME);
        server.verify();
    }

    @Test
    @DisplayName("token 无效时返回空")
    void validateTokenAndGetUsername_invalidToken_returnsEmpty() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo(buildUrl()))
                .andRespond(withSuccess(invalidTokenResponse(), MediaType.APPLICATION_JSON));

        HomeSsoClient client = new HomeSsoClient(new TestCrmHttpClient(restTemplate, properties), properties);
        Optional<String> result = client.validateTokenAndGetUsername(INVALID_TOKEN);

        assertThat(result).isEmpty();
        server.verify();
    }

    @Test
    @DisplayName("网络异常时返回空")
    void validateTokenAndGetUsername_networkError_returnsEmpty() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo(buildUrl()))
                .andRespond(withServerError());

        HomeSsoClient client = new HomeSsoClient(new TestCrmHttpClient(restTemplate, properties), properties);
        Optional<String> result = client.validateTokenAndGetUsername(VALID_TOKEN);

        assertThat(result).isEmpty();
        server.verify();
    }

    @Test
    @DisplayName("响应中缺少 username 时返回空")
    void validateTokenAndGetUsername_missingUsername_returnsEmpty() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo(buildUrl()))
                .andRespond(withSuccess("""
                        {
                          "code": 0,
                          "message": "success",
                          "data": {
                            "nickName": "测试用户",
                            "status": 1
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        HomeSsoClient client = new HomeSsoClient(new TestCrmHttpClient(restTemplate, properties), properties);
        Optional<String> result = client.validateTokenAndGetUsername(VALID_TOKEN);

        assertThat(result).isEmpty();
        server.verify();
    }

    @Test
    @DisplayName("data 为 null 时返回空")
    void validateTokenAndGetUsername_nullData_returnsEmpty() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo(buildUrl()))
                .andRespond(withSuccess("""
                        {
                          "code": 0,
                          "message": "success",
                          "data": null
                        }
                        """, MediaType.APPLICATION_JSON));

        HomeSsoClient client = new HomeSsoClient(new TestCrmHttpClient(restTemplate, properties), properties);
        Optional<String> result = client.validateTokenAndGetUsername(VALID_TOKEN);

        assertThat(result).isEmpty();
        server.verify();
    }

    private static class TestCrmHttpClient extends CrmHttpClient {
        private final RestTemplate restTemplate;

        TestCrmHttpClient(RestTemplate restTemplate, CrmProperties properties) {
            super(properties);
            this.restTemplate = restTemplate;
        }

        @Override
        public CrmResponseHandler.CrmApiResponse get(String baseUrl, String path, String accessToken) {
            String url = baseUrl + path;
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.setBearerAuth(accessToken);
            org.springframework.http.HttpEntity<Void> request = new org.springframework.http.HttpEntity<>(headers);
            try {
                org.springframework.http.ResponseEntity<String> response = restTemplate.exchange(
                        url, org.springframework.http.HttpMethod.GET, request, String.class);
                return CrmResponseHandler.parse(response.getBody());
            } catch (RuntimeException e) {
                return CrmResponseHandler.CrmApiResponse.parseError(e.getMessage());
            }
        }
    }
}
