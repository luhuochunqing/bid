package com.xiyu.bid.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ExceptionMessageSanitizer} 单元测试.
 *
 * <p>纯单元测试，不启动 Spring 容器. 覆盖：
 * <ul>
 *   <li>{@code sanitize(Throwable)} 所有分支（null / AppFailure / External / Spring Security / 未受控）</li>
 *   <li>{@code resolveOrDefault(String, String)} 各种空值组合</li>
 *   <li>{@code isMessageSafe(Throwable)} 白名单判定</li>
 * </ul>
 *
 * <p>测试中通过自定义 {@link AppFailureException} 子类 {@code TestAppFailure}
 * 构造 userMessage 为 null/空白的场景，覆盖 sanitize 的 fallback 分支.
 */
@DisplayName("ExceptionMessageSanitizer 异常消息脱敏器")
class ExceptionMessageSanitizerTest {

    // ============================== sanitize(Throwable) ==============================

    @Nested
    @DisplayName("sanitize(Throwable)")
    class Sanitize {

        @Test
        @DisplayName("传入 null → 返回默认未受控消息")
        void 传入null_返回默认未受控消息() {
            String result = ExceptionMessageSanitizer.sanitize(null);

            assertThat(result).isEqualTo(ExceptionMessageSanitizer.DEFAULT_UNCONTROLLED_MESSAGE);
        }

        @Test
        @DisplayName("AppFailureException 子类 userMessage 非空 → 返回 userMessage")
        void appFailure_userMessage非空_返回userMessage() {
            ResourceNotFoundException ex = new ResourceNotFoundException("AlertRule", "abc-123");

            String result = ExceptionMessageSanitizer.sanitize(ex);

            assertThat(result).isEqualTo("请求的资源不存在");
        }

        @Test
        @DisplayName("BusinessException 透传其 userMessage")
        void businessException_透传userMessage() {
            BusinessException ex = new BusinessException("订单状态不可变更");

            String result = ExceptionMessageSanitizer.sanitize(ex);

            assertThat(result).isEqualTo("订单状态不可变更");
        }

        @Test
        @DisplayName("AppFailureException 子类 userMessage 为 null → 返回业务默认消息")
        void appFailure_userMessage为null_返回业务默认消息() {
            TestAppFailure ex = new TestAppFailure(null);

            String result = ExceptionMessageSanitizer.sanitize(ex);

            assertThat(result).isEqualTo(ExceptionMessageSanitizer.DEFAULT_BUSINESS_MESSAGE);
        }

        @Test
        @DisplayName("AppFailureException 子类 userMessage 为空白 → 返回业务默认消息")
        void appFailure_userMessage为空白_返回业务默认消息() {
            TestAppFailure ex = new TestAppFailure("   ");

            String result = ExceptionMessageSanitizer.sanitize(ex);

            assertThat(result).isEqualTo(ExceptionMessageSanitizer.DEFAULT_BUSINESS_MESSAGE);
        }

        @Test
        @DisplayName("ExternalServiceException userFriendlyMessage 非空 → 返回该消息")
        void externalService_userFriendlyMessage非空_返回该消息() {
            ExternalServiceException ex = ExternalServiceException.forService(
                    "AI API", 503, "AI 服务暂不可用，请稍后重试", "upstream raw", null);

            String result = ExceptionMessageSanitizer.sanitize(ex);

            assertThat(result).isEqualTo("AI 服务暂不可用，请稍后重试");
        }

        @Test
        @DisplayName("ExternalServiceException userFriendlyMessage 为 null → 返回外部服务默认消息")
        void externalService_userFriendlyMessage为null_返回外部服务默认消息() {
            ExternalServiceException ex = ExternalServiceException.forService(
                    "AI API", 500, null, "raw", null);

            String result = ExceptionMessageSanitizer.sanitize(ex);

            assertThat(result).isEqualTo(ExceptionMessageSanitizer.DEFAULT_EXTERNAL_SERVICE_MESSAGE);
        }

        @Test
        @DisplayName("ExternalServiceException userFriendlyMessage 为空白 → 返回外部服务默认消息")
        void externalService_userFriendlyMessage为空白_返回外部服务默认消息() {
            ExternalServiceException ex = ExternalServiceException.forService(
                    "AI API", 500, "   ", "raw", null);

            String result = ExceptionMessageSanitizer.sanitize(ex);

            assertThat(result).isEqualTo(ExceptionMessageSanitizer.DEFAULT_EXTERNAL_SERVICE_MESSAGE);
        }

        @Test
        @DisplayName("Spring Security AuthenticationException → 返回认证失败消息")
        void springAuthenticationException_返回认证失败消息() {
            BadCredentialsException ex = new BadCredentialsException("令牌已过期");

            String result = ExceptionMessageSanitizer.sanitize(ex);

            assertThat(result).isEqualTo("认证失败，请重新登录");
            assertThat(result).doesNotContain("令牌");
        }

        @Test
        @DisplayName("Spring Security AccessDeniedException → 返回权限不足消息")
        void springAccessDeniedException_返回权限不足消息() {
            AccessDeniedException ex = new AccessDeniedException("access denied");

            String result = ExceptionMessageSanitizer.sanitize(ex);

            assertThat(result).isEqualTo("权限不足，无法访问该资源");
            assertThat(result).doesNotContain("access denied");
        }

        @Test
        @DisplayName("IllegalArgumentException 含 SQL 片段 → 返回默认未受控消息，不透传 SQL")
        void illegalArgument_含SQL片段_返回默认未受控消息且不透传SQL() {
            IllegalArgumentException ex = new IllegalArgumentException(
                    "select * from users where id=1; SQL constraint violation");

            String result = ExceptionMessageSanitizer.sanitize(ex);

            assertThat(result)
                    .isEqualTo(ExceptionMessageSanitizer.DEFAULT_UNCONTROLLED_MESSAGE)
                    .doesNotContain("SQL")
                    .doesNotContain("select")
                    .doesNotContain("users");
        }

        @Test
        @DisplayName("IllegalArgumentException 含 INSERT 语句 → 返回默认未受控消息")
        void illegalArgument_含INSERT语句_返回默认未受控消息() {
            IllegalArgumentException ex = new IllegalArgumentException(
                    "insert into users (name) values ('test')");

            String result = ExceptionMessageSanitizer.sanitize(ex);

            assertThat(result).isEqualTo(ExceptionMessageSanitizer.DEFAULT_UNCONTROLLED_MESSAGE);
            assertThat(result).doesNotContain("insert").doesNotContain("users");
        }

        @Test
        @DisplayName("IllegalArgumentException 简单业务文案 → 透传 message")
        void illegalArgument_简单业务文案_透传message() {
            // 模拟 DocInsightController / TemplateController 等抛出的业务校验消息
            IllegalArgumentException ex = new IllegalArgumentException("无效的文件 URL 格式");

            String result = ExceptionMessageSanitizer.sanitize(ex);

            assertThat(result).isEqualTo("无效的文件 URL 格式");
        }

        @Test
        @DisplayName("IllegalArgumentException 业务校验文案（含中文标点）→ 透传 message")
        void illegalArgument_业务校验文案含中文标点_透传message() {
            IllegalArgumentException ex = new IllegalArgumentException("产品类型、行业、文档类型不能为空");

            String result = ExceptionMessageSanitizer.sanitize(ex);

            assertThat(result).isEqualTo("产品类型、行业、文档类型不能为空");
        }

        @Test
        @DisplayName("IllegalArgumentException 含 Java 包路径 → 不透传")
        void illegalArgument_含Java包路径_返回默认未受控消息() {
            IllegalArgumentException ex = new IllegalArgumentException(
                    "Failed to invoke com.xiyu.bid.service.UserService method");

            String result = ExceptionMessageSanitizer.sanitize(ex);

            assertThat(result).isEqualTo(ExceptionMessageSanitizer.DEFAULT_UNCONTROLLED_MESSAGE);
            assertThat(result).doesNotContain("com.xiyu").doesNotContain("UserService");
        }

        @Test
        @DisplayName("IllegalArgumentException 含堆栈痕迹 → 不透传")
        void illegalArgument_含堆栈痕迹_返回默认未受控消息() {
            IllegalArgumentException ex = new IllegalArgumentException(
                    "Error at com.xiyu.bid.UserService.findById(UserService.java:42)");

            String result = ExceptionMessageSanitizer.sanitize(ex);

            assertThat(result).isEqualTo(ExceptionMessageSanitizer.DEFAULT_UNCONTROLLED_MESSAGE);
            assertThat(result).doesNotContain("UserService.java");
        }

        @Test
        @DisplayName("IllegalArgumentException 超长 message（>200 字符）→ 不透传")
        void illegalArgument_超长message_返回默认未受控消息() {
            String longMessage = "a".repeat(250);

            IllegalArgumentException ex = new IllegalArgumentException(longMessage);

            String result = ExceptionMessageSanitizer.sanitize(ex);

            assertThat(result).isEqualTo(ExceptionMessageSanitizer.DEFAULT_UNCONTROLLED_MESSAGE);
        }

        @Test
        @DisplayName("IllegalArgumentException null message → 返回默认未受控消息")
        void illegalArgument_nullMessage_返回默认未受控消息() {
            IllegalArgumentException ex = new IllegalArgumentException((String) null);

            String result = ExceptionMessageSanitizer.sanitize(ex);

            assertThat(result).isEqualTo(ExceptionMessageSanitizer.DEFAULT_UNCONTROLLED_MESSAGE);
        }

        @Test
        @DisplayName("IllegalArgumentException 空白 message → 返回默认未受控消息")
        void illegalArgument_空白message_返回默认未受控消息() {
            IllegalArgumentException ex = new IllegalArgumentException("   ");

            String result = ExceptionMessageSanitizer.sanitize(ex);

            assertThat(result).isEqualTo(ExceptionMessageSanitizer.DEFAULT_UNCONTROLLED_MESSAGE);
        }

        @Test
        @DisplayName("NullPointerException → 返回默认未受控消息，不透传原始 message")
        void nullPointer_返回默认未受控消息() {
            NullPointerException ex = new NullPointerException("user is null");

            String result = ExceptionMessageSanitizer.sanitize(ex);

            assertThat(result).isEqualTo(ExceptionMessageSanitizer.DEFAULT_UNCONTROLLED_MESSAGE);
            assertThat(result).doesNotContain("user");
        }

        @Test
        @DisplayName("普通 RuntimeException → 返回默认未受控消息，不透传内部细节")
        void runtimeException_返回默认未受控消息() {
            RuntimeException ex = new RuntimeException("internal wiring failure");

            String result = ExceptionMessageSanitizer.sanitize(ex);

            assertThat(result).isEqualTo(ExceptionMessageSanitizer.DEFAULT_UNCONTROLLED_MESSAGE);
            assertThat(result).doesNotContain("internal");
        }
    }

    // ============================== resolveOrDefault(String, String) ==============================

    @Nested
    @DisplayName("resolveOrDefault(String, String)")
    class ResolveOrDefault {

        @Test
        @DisplayName("customMessage 非空 → 返回 customMessage")
        void customMessage非空_返回customMessage() {
            String result = ExceptionMessageSanitizer.resolveOrDefault("自定义消息", "默认消息");

            assertThat(result).isEqualTo("自定义消息");
        }

        @Test
        @DisplayName("customMessage 为 null → 返回 defaultMessage")
        void customMessage为null_返回defaultMessage() {
            String result = ExceptionMessageSanitizer.resolveOrDefault(null, "默认消息");

            assertThat(result).isEqualTo("默认消息");
        }

        @Test
        @DisplayName("customMessage 为空白 → 返回 defaultMessage")
        void customMessage为空白_返回defaultMessage() {
            String result = ExceptionMessageSanitizer.resolveOrDefault("   ", "默认消息");

            assertThat(result).isEqualTo("默认消息");
        }

        @Test
        @DisplayName("两者都为 null → 返回默认未受控消息")
        void 两者都为null_返回默认未受控消息() {
            String result = ExceptionMessageSanitizer.resolveOrDefault(null, null);

            assertThat(result).isEqualTo(ExceptionMessageSanitizer.DEFAULT_UNCONTROLLED_MESSAGE);
        }

        @Test
        @DisplayName("两者都为空白 → 返回默认未受控消息")
        void 两者都为空白_返回默认未受控消息() {
            String result = ExceptionMessageSanitizer.resolveOrDefault("   ", "   ");

            assertThat(result).isEqualTo(ExceptionMessageSanitizer.DEFAULT_UNCONTROLLED_MESSAGE);
        }

        @Test
        @DisplayName("customMessage 为 null、defaultMessage 为空白 → 返回默认未受控消息")
        void customMessage为null_defaultMessage为空白_返回默认未受控消息() {
            String result = ExceptionMessageSanitizer.resolveOrDefault(null, "  ");

            assertThat(result).isEqualTo(ExceptionMessageSanitizer.DEFAULT_UNCONTROLLED_MESSAGE);
        }
    }

    // ============================== isMessageSafe(Throwable) ==============================

    @Nested
    @DisplayName("isMessageSafe(Throwable)")
    class IsMessageSafe {

        @Test
        @DisplayName("AppFailureException 子类 → true")
        void appFailure_返回true() {
            ResourceNotFoundException ex = new ResourceNotFoundException("AlertRule", "123");

            assertThat(ExceptionMessageSanitizer.isMessageSafe(ex)).isTrue();
        }

        @Test
        @DisplayName("ExternalServiceException → true")
        void externalService_返回true() {
            ExternalServiceException ex = ExternalServiceException.forService(
                    "AI API", 503, "AI 暂不可用", "raw", null);

            assertThat(ExceptionMessageSanitizer.isMessageSafe(ex)).isTrue();
        }

        @Test
        @DisplayName("IllegalArgumentException → false")
        void illegalArgument_返回false() {
            IllegalArgumentException ex = new IllegalArgumentException("bad arg");

            assertThat(ExceptionMessageSanitizer.isMessageSafe(ex)).isFalse();
        }

        @Test
        @DisplayName("null → false")
        void null_返回false() {
            assertThat(ExceptionMessageSanitizer.isMessageSafe(null)).isFalse();
        }

        @Test
        @DisplayName("普通 RuntimeException → false")
        void runtimeException_返回false() {
            assertThat(ExceptionMessageSanitizer.isMessageSafe(new RuntimeException("err"))).isFalse();
        }
    }

    // ============================== 测试辅助类 ==============================

    /**
     * 测试专用 {@link AppFailureException} 子类.
     *
     * <p>用于构造 userMessage 为 null/空白的场景，覆盖 {@code sanitize} 的 fallback 分支.
     * 项目内既有子类（{@link BusinessException}、{@link ResourceNotFoundException}）始终把
     * userMessage 写成非空，无法触发 fallback 路径.
     */
    private static final class TestAppFailure extends AppFailureException {
        TestAppFailure(String userMessage) {
            super(
                    ErrorCategory.BUSINESS_UNAVAILABLE,
                    400,
                    HttpStatus.BAD_REQUEST,
                    userMessage,
                    false,
                    false,
                    false,
                    "test_key",
                    "internal-message",
                    null
            );
        }
    }

    // ============================== CRLF 注入防护 ==============================

    @Nested
    @DisplayName("CRLF 注入防护")
    class CrlfInjection {

        @Test
        @DisplayName("sanitize 受控异常的 userMessage 含 CRLF → 被替换为空格")
        void sanitize_受控异常含CRLF_应被替换为空格() {
            BusinessException ex = new BusinessException(409, "正常消息\r\n伪造日志行");

            String result = ExceptionMessageSanitizer.sanitize(ex);

            assertThat(result).doesNotContain("\r").doesNotContain("\n");
            assertThat(result).contains("正常消息");
            assertThat(result).contains("伪造日志行");
        }

        @Test
        @DisplayName("resolveOrDefault customMessage 含 CRLF → 被替换为空格")
        void resolveOrDefault_customMessage含CRLF_应被替换为空格() {
            String result = ExceptionMessageSanitizer.resolveOrDefault(
                    "正常消息\r\n伪造日志行", "默认消息");

            assertThat(result).doesNotContain("\r").doesNotContain("\n");
            assertThat(result).contains("正常消息");
            assertThat(result).contains("伪造日志行");
        }

        @Test
        @DisplayName("resolveOrDefault defaultMessage 含 CRLF → 被替换为空格")
        void resolveOrDefault_defaultMessage含CRLF_应被替换为空格() {
            String result = ExceptionMessageSanitizer.resolveOrDefault(
                    null, "默认消息\r\n伪造日志行");

            assertThat(result).doesNotContain("\r").doesNotContain("\n");
            assertThat(result).contains("默认消息");
        }

        @Test
        @DisplayName("sanitize 含换行符的 RoleNotAuthorizedException → 被替换为空格")
        void sanitize_RoleNotAuthorizedException含CRLF_应被替换为空格() {
            RoleNotAuthorizedException ex = new RoleNotAuthorizedException("OSS 角色不在白名单\n注入行");

            String result = ExceptionMessageSanitizer.sanitize(ex);

            assertThat(result).doesNotContain("\n");
            assertThat(result).contains("OSS 角色不在白名单");
            assertThat(result).contains("注入行");
        }

        @Test
        @DisplayName("sanitize 含 \r 字符的 ExternalServiceException → 被替换为空格")
        void sanitize_ExternalServiceException含CR_应被替换为空格() {
            ExternalServiceException ex = ExternalServiceException.forService(
                    "AI API", 402, "余额不足\r注入", "raw", null);

            String result = ExceptionMessageSanitizer.sanitize(ex);

            assertThat(result).doesNotContain("\r");
            assertThat(result).contains("余额不足");
            assertThat(result).contains("注入");
        }
    }
}
