package com.xiyu.bid.exception;

import com.xiyu.bid.dto.ApiResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ExceptionResponseStrategy 单元测试。
 *
 * <p>覆盖异常响应策略的所有公开方法,验证:
 * <ul>
 *   <li>受控异常(AppFailureException / ExternalServiceException)的 userMessage 正确透传</li>
 *   <li>未受控异常(IllegalArgumentException 等)的 message 永不透传,使用硬编码默认值</li>
 *   <li>customCode / fixedMessage / prefix 的各种组合行为</li>
 * </ul>
 *
 * <p>纯函数测试,不启动 Spring 容器。
 */
@DisplayName("ExceptionResponseStrategy 异常响应策略")
class ExceptionResponseStrategyTest {

    // ====================================================
    // 1. buildResponse(Throwable ex, HttpStatus httpStatus)
    // ====================================================
    @Nested
    @DisplayName("buildResponse(ex, httpStatus):默认使用安全消息")
    class BuildResponseDefault {

        @Test
        @DisplayName("BusinessException(409,...) → code=409, message 透传 userMessage")
        void businessException_应透传用户消息并使用HttpStatusValue作为Code() {
            BusinessException ex = new BusinessException(409, "投标文件已进入结项阶段");

            ApiResponse<Void> response = ExceptionResponseStrategy.buildResponse(ex, HttpStatus.CONFLICT);

            assertThat(response.getCode()).isEqualTo(409);
            assertThat(response.getMessage()).isEqualTo("投标文件已进入结项阶段");
            assertThat(response.isSuccess()).isFalse();
        }

        @Test
        @DisplayName("IllegalArgumentException(SQL...) → code=400, message 不包含 SQL")
        void illegalArgumentException_应使用默认消息且不透传敏感内容() {
            IllegalArgumentException ex = new IllegalArgumentException("SQL error at line 42");

            ApiResponse<Void> response = ExceptionResponseStrategy.buildResponse(ex, HttpStatus.BAD_REQUEST);

            assertThat(response.getCode()).isEqualTo(400);
            assertThat(response.getMessage()).doesNotContain("SQL");
            assertThat(response.getMessage()).doesNotContain("line 42");
            assertThat(response.getMessage()).isEqualTo("请求处理失败");
        }

        @Test
        @DisplayName("null ex → 返回默认消息")
        void nullEx_应返回默认消息() {
            ApiResponse<Void> response = ExceptionResponseStrategy.buildResponse(null, HttpStatus.INTERNAL_SERVER_ERROR);

            assertThat(response.getCode()).isEqualTo(500);
            assertThat(response.getMessage()).isEqualTo("请求处理失败");
            assertThat(response.isSuccess()).isFalse();
        }

        @Test
        @DisplayName("ResourceNotFoundException → userMessage 透传(受控异常)")
        void resourceNotFoundException_应透传固定userMessage() {
            ResourceNotFoundException ex = new ResourceNotFoundException("Tender", "123");

            ApiResponse<Void> response = ExceptionResponseStrategy.buildResponse(ex, HttpStatus.NOT_FOUND);

            assertThat(response.getCode()).isEqualTo(404);
            assertThat(response.getMessage()).isEqualTo("请求的资源不存在");
        }
    }

    // ====================================================
    // 2. buildResponse(ex, httpStatus, customCode)
    // ====================================================
    @Nested
    @DisplayName("buildResponse(ex, httpStatus, customCode):使用自定义错误码")
    class BuildResponseWithCustomCode {

        @Test
        @DisplayName("AppFailureException 子类 → 使用 customCode 而非 httpStatus.value()")
        void appFailureException_应使用customCode而非HttpStatusValue() {
            BusinessException ex = new BusinessException(409, "投标文件已进入结项阶段");

            // 传入 HttpStatus.INTERNAL_SERVER_ERROR(500) 但 customCode=4090
            ApiResponse<Void> response = ExceptionResponseStrategy.buildResponse(
                    ex, HttpStatus.INTERNAL_SERVER_ERROR, 4090);

            assertThat(response.getCode()).isEqualTo(4090);
            assertThat(response.getCode()).isNotEqualTo(500);
            assertThat(response.getMessage()).isEqualTo("投标文件已进入结项阶段");
        }

        @Test
        @DisplayName("ResourceNotFoundException → customCode 与 message 都正确")
        void resourceNotFoundException_customCode和message都正确() {
            ResourceNotFoundException ex = new ResourceNotFoundException("Tender", "123");

            ApiResponse<Void> response = ExceptionResponseStrategy.buildResponse(
                    ex, HttpStatus.NOT_FOUND, 4040);

            assertThat(response.getCode()).isEqualTo(4040);
            assertThat(response.getMessage()).isEqualTo("请求的资源不存在");
        }

        @Test
        @DisplayName("未受控异常 + customCode → customCode 生效,message 仍走默认")
        void illegalArgumentException_customCode生效但message走默认() {
            IllegalArgumentException ex = new IllegalArgumentException("SQL error at line 42");

            ApiResponse<Void> response = ExceptionResponseStrategy.buildResponse(
                    ex, HttpStatus.BAD_REQUEST, 4222);

            assertThat(response.getCode()).isEqualTo(4222);
            assertThat(response.getMessage()).doesNotContain("SQL");
            assertThat(response.getMessage()).isEqualTo("请求处理失败");
        }
    }

    // ====================================================
    // 3. buildFixedResponse(httpStatus, fixedMessage)
    // ====================================================
    @Nested
    @DisplayName("buildFixedResponse(httpStatus, fixedMessage):硬编码消息")
    class BuildFixedResponse {

        @Test
        @DisplayName("硬编码消息不依赖 ex")
        void 硬编码消息应直接使用传入的fixedMessage() {
            ApiResponse<Void> response = ExceptionResponseStrategy.buildFixedResponse(
                    HttpStatus.UNAUTHORIZED, "请先登录");

            assertThat(response.getCode()).isEqualTo(401);
            assertThat(response.getMessage()).isEqualTo("请先登录");
            assertThat(response.isSuccess()).isFalse();
        }

        @Test
        @DisplayName("FORBIDDEN + 固定文案")
        void forbidden_固定文案() {
            ApiResponse<Void> response = ExceptionResponseStrategy.buildFixedResponse(
                    HttpStatus.FORBIDDEN, "权限不足,无法访问该资源");

            assertThat(response.getCode()).isEqualTo(403);
            assertThat(response.getMessage()).isEqualTo("权限不足,无法访问该资源");
        }

        @Test
        @DisplayName("INTERNAL_SERVER_ERROR + 固定文案")
        void internalServerError_固定文案() {
            ApiResponse<Void> response = ExceptionResponseStrategy.buildFixedResponse(
                    HttpStatus.INTERNAL_SERVER_ERROR, "系统异常,请联系管理员");

            assertThat(response.getCode()).isEqualTo(500);
            assertThat(response.getMessage()).isEqualTo("系统异常,请联系管理员");
        }
    }

    // ====================================================
    // 4. buildWithPrefix(ex, httpStatus, prefix)
    // ====================================================
    @Nested
    @DisplayName("buildWithPrefix(ex, httpStatus, prefix):带前缀响应")
    class BuildWithPrefix {

        @Test
        @DisplayName("AppFailureException(受控异常) → 'PREFIX: userMessage'")
        void appFailureException_应拼接prefix和userMessage() {
            BusinessException ex = new BusinessException(409, "投标文件已进入结项阶段");

            ApiResponse<Void> response = ExceptionResponseStrategy.buildWithPrefix(
                    ex, HttpStatus.FORBIDDEN, "ROLE_NOT_AUTHORIZED");

            assertThat(response.getCode()).isEqualTo(403);
            assertThat(response.getMessage()).isEqualTo("ROLE_NOT_AUTHORIZED: 投标文件已进入结项阶段");
        }

        @Test
        @DisplayName("IllegalArgumentException(未受控) → 仅返回 PREFIX,不透传")
        void illegalArgumentException_应仅返回prefix() {
            IllegalArgumentException ex = new IllegalArgumentException("SQL error at line 42");

            ApiResponse<Void> response = ExceptionResponseStrategy.buildWithPrefix(
                    ex, HttpStatus.FORBIDDEN, "ROLE_NOT_AUTHORIZED");

            assertThat(response.getCode()).isEqualTo(403);
            assertThat(response.getMessage()).isEqualTo("ROLE_NOT_AUTHORIZED");
            assertThat(response.getMessage()).doesNotContain("SQL");
            assertThat(response.getMessage()).doesNotContain("line 42");
        }

        @Test
        @DisplayName("ExternalServiceException(受控异常) → 'PREFIX: userFriendlyMessage'")
        void externalServiceException_应拼接prefix和friendlyMessage() {
            ExternalServiceException ex = ExternalServiceException.forService(
                    "AI API", 402, "余额不足,请充值后重试", "Insufficient balance", null);

            ApiResponse<Void> response = ExceptionResponseStrategy.buildWithPrefix(
                    ex, HttpStatus.BAD_GATEWAY, "AI_PROVIDER_ERROR");

            assertThat(response.getCode()).isEqualTo(502);
            assertThat(response.getMessage()).isEqualTo("AI_PROVIDER_ERROR: 余额不足,请充值后重试");
        }

        @Test
        @DisplayName("null ex → 仅返回 PREFIX")
        void nullEx_仅返回prefix() {
            ApiResponse<Void> response = ExceptionResponseStrategy.buildWithPrefix(
                    null, HttpStatus.FORBIDDEN, "ROLE_NOT_AUTHORIZED");

            assertThat(response.getCode()).isEqualTo(403);
            assertThat(response.getMessage()).isEqualTo("ROLE_NOT_AUTHORIZED");
        }

        @Test
        @DisplayName("ResourceNotFoundException(受控异常) → 'PREFIX: userMessage'")
        void resourceNotFoundException_应拼接prefix和userMessage() {
            ResourceNotFoundException ex = new ResourceNotFoundException("Tender", "123");

            ApiResponse<Void> response = ExceptionResponseStrategy.buildWithPrefix(
                    ex, HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND");

            assertThat(response.getCode()).isEqualTo(404);
            assertThat(response.getMessage()).isEqualTo("RESOURCE_NOT_FOUND: 请求的资源不存在");
        }
    }
}
