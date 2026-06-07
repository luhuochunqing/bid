package com.xiyu.bid.tendersource.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TenderSourceConnectionTestPolicy - pure connection test logic")
class TenderSourceConnectionTestPolicyTest {

    @Nested
    @DisplayName("platform validation")
    class PlatformValidation {

        @Test
        @DisplayName("returns failure when platform is not third-party service")
        void nonThirdPartyPlatform_returnsFailure() {
            TenderSourceConnectionResult result = TenderSourceConnectionTestPolicy.testThirdPartyConnection(
                    "中国政府采购网",
                    "https://api.example.com",
                    "test-key"
            );

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).isEqualTo("仅支持测试「第三方商机服务」平台的连接");
        }

        @Test
        @DisplayName("accepts third-party service platform")
        void thirdPartyPlatform_accepted() {
            TenderSourceConnectionResult result = TenderSourceConnectionTestPolicy.testThirdPartyConnection(
                    "第三方商机服务",
                    "https://api.example.com",
                    "test-key"
            );

            // Will fail due to invalid URL, but not due to platform validation
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).isNotEqualTo("仅支持测试「第三方商机服务」平台的连接");
        }
    }

    @Nested
    @DisplayName("input validation")
    class InputValidation {

        @Test
        @DisplayName("returns failure when apiEndpoint is blank")
        void blankApiEndpoint_returnsFailure() {
            TenderSourceConnectionResult result = TenderSourceConnectionTestPolicy.testThirdPartyConnection(
                    "第三方商机服务",
                    "   ",
                    "test-key"
            );

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).isEqualTo("API端点不能为空");
        }

        @Test
        @DisplayName("returns failure when apiEndpoint is null")
        void nullApiEndpoint_returnsFailure() {
            TenderSourceConnectionResult result = TenderSourceConnectionTestPolicy.testThirdPartyConnection(
                    "第三方商机服务",
                    null,
                    "test-key"
            );

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).isEqualTo("API端点不能为空");
        }

        @Test
        @DisplayName("returns failure when apiKey is blank")
        void blankApiKey_returnsFailure() {
            TenderSourceConnectionResult result = TenderSourceConnectionTestPolicy.testThirdPartyConnection(
                    "第三方商机服务",
                    "https://api.example.com",
                    ""
            );

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).isEqualTo("API密钥不能为空");
        }

        @Test
        @DisplayName("returns failure when apiKey is null")
        void nullApiKey_returnsFailure() {
            TenderSourceConnectionResult result = TenderSourceConnectionTestPolicy.testThirdPartyConnection(
                    "第三方商机服务",
                    "https://api.example.com",
                    null
            );

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).isEqualTo("API密钥不能为空");
        }
    }

    @Nested
    @DisplayName("URL validation")
    class UrlValidation {

        @Test
        @DisplayName("returns failure for invalid URL format")
        void invalidUrlFormat_returnsFailure() {
            TenderSourceConnectionResult result = TenderSourceConnectionTestPolicy.testThirdPartyConnection(
                    "第三方商机服务",
                    "not-a-valid-url",
                    "test-key"
            );

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).isEqualTo("API端点格式无效");
        }

        @Test
        @DisplayName("accepts valid https URL")
        void validHttpsUrl_accepted() {
            TenderSourceConnectionResult result = TenderSourceConnectionTestPolicy.testThirdPartyConnection(
                    "第三方商机服务",
                    "https://api.example.com/v1/opportunities",
                    "test-key"
            );

            // Will fail due to network, but not URL format
            assertThat(result.getMessage()).isNotEqualTo("API端点格式无效");
        }

        @Test
        @DisplayName("accepts valid http URL")
        void validHttpUrl_accepted() {
            TenderSourceConnectionResult result = TenderSourceConnectionTestPolicy.testThirdPartyConnection(
                    "第三方商机服务",
                    "http://api.example.com/v1/opportunities",
                    "test-key"
            );

            // Will fail due to network, but not URL format
            assertThat(result.getMessage()).isNotEqualTo("API端点格式无效");
        }

        @Test
        @DisplayName("trims whitespace from apiEndpoint")
        void trimmedUrl_accepted() {
            TenderSourceConnectionResult result = TenderSourceConnectionTestPolicy.testThirdPartyConnection(
                    "第三方商机服务",
                    "  https://api.example.com  ",
                    "test-key"
            );

            // Should not fail on whitespace trimming
            assertThat(result.getMessage()).isNotEqualTo("API端点不能为空");
            assertThat(result.getMessage()).isNotEqualTo("API端点格式无效");
        }
    }

    @Nested
    @DisplayName("TenderSourceConnectionResult value object")
    class ConnectionResultTests {

        @Test
        @DisplayName("success result has correct message")
        void successResult_hasCorrectMessage() {
            TenderSourceConnectionResult result = TenderSourceConnectionResult.success();

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getMessage()).isEqualTo("连接测试成功");
        }

        @Test
        @DisplayName("failure result with message preserves message")
        void failureResult_withMessage_preservesMessage() {
            TenderSourceConnectionResult result = TenderSourceConnectionResult.failure("自定义错误信息");

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).isEqualTo("自定义错误信息");
        }

        @Test
        @DisplayName("failure result with null message uses default")
        void failureResult_withNullMessage_usesDefault() {
            TenderSourceConnectionResult result = TenderSourceConnectionResult.failure(null);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).isEqualTo("连接失败，请检查API端点和密钥");
        }

        @Test
        @DisplayName("failure result with blank message uses default")
        void failureResult_withBlankMessage_usesDefault() {
            TenderSourceConnectionResult result = TenderSourceConnectionResult.failure("   ");

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).isEqualTo("连接失败，请检查API端点和密钥");
        }
    }
}
