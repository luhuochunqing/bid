package com.xiyu.bid.tendersource.application;

import com.xiyu.bid.tendersource.dto.TenderSourceTestRequest;
import com.xiyu.bid.tendersource.dto.TenderSourceTestResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TenderSourceConnectionTestService - imperative shell")
class TenderSourceConnectionTestServiceTest {

    private final TenderSourceConnectionTestService service = new TenderSourceConnectionTestService();

    @Nested
    @DisplayName("request validation")
    class RequestValidation {

        @Test
        @DisplayName("returns failure when request is null")
        void nullRequest_returnsFailure() {
            TenderSourceTestResponse response = service.testConnection(null);

            assertThat(response.isSuccess()).isFalse();
            assertThat(response.getMessage()).isEqualTo("请求参数不能为空");
        }
    }

    @Nested
    @DisplayName("platform validation")
    class PlatformValidation {

        @Test
        @DisplayName("returns failure for non-third-party platform")
        void nonThirdPartyPlatform_returnsFailure() {
            TenderSourceTestRequest request = TenderSourceTestRequest.builder()
                    .platform("中国政府采购网")
                    .apiEndpoint("https://api.example.com")
                    .apiKey("test-key")
                    .build();

            TenderSourceTestResponse response = service.testConnection(request);

            assertThat(response.isSuccess()).isFalse();
            assertThat(response.getMessage()).isEqualTo("仅支持测试「第三方商机服务」平台的连接");
        }
    }

    @Nested
    @DisplayName("TenderSourceTestResponse DTO")
    class ResponseDtoTests {

        @Test
        @DisplayName("success response has correct structure")
        void successResponse_hasCorrectStructure() {
            TenderSourceTestResponse response = TenderSourceTestResponse.success();

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getMessage()).isEqualTo("连接测试成功");
            assertThat(response.getTestedAt()).isNotNull();
        }

        @Test
        @DisplayName("failure response preserves error message")
        void failureResponse_preservesMessage() {
            TenderSourceTestResponse response = TenderSourceTestResponse.failure("认证失败");

            assertThat(response.isSuccess()).isFalse();
            assertThat(response.getMessage()).isEqualTo("认证失败");
            assertThat(response.getTestedAt()).isNotNull();
        }

        @Test
        @DisplayName("failure response uses default message for null")
        void failureResponse_nullMessage_usesDefault() {
            TenderSourceTestResponse response = TenderSourceTestResponse.failure(null);

            assertThat(response.isSuccess()).isFalse();
            assertThat(response.getMessage()).isEqualTo("连接失败，请检查API端点和密钥");
        }
    }
}
