package com.xiyu.bid.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class AppFailureExceptionTest {

    @Test
    void businessException_shouldMapToBusinessUnavailableCategory() {
        BusinessException exception = new BusinessException(409, "业务暂不可用");

        assertThat(exception.getCategory()).isEqualTo(ErrorCategory.BUSINESS_UNAVAILABLE);
        assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(exception.getCode()).isEqualTo(409);
        assertThat(exception.getUserMessage()).isEqualTo("业务暂不可用");
        assertThat(exception.isRetryable()).isFalse();
    }

    @Test
    void resourceNotFoundException_shouldMapToResourceNotFoundCategory() {
        ResourceNotFoundException exception = new ResourceNotFoundException("AlertRule", "123");

        assertThat(exception.getCategory()).isEqualTo(ErrorCategory.RESOURCE_NOT_FOUND);
        assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(exception.getCode()).isEqualTo(404);
        assertThat(exception.getUserMessage()).isEqualTo("请求的资源不存在");
    }

    @Test
    void retryableOperationException_shouldExposeRetryablePolicy() {
        RetryableOperationException exception = new RetryableOperationException(409, HttpStatus.CONFLICT, "请刷新后重试");

        assertThat(exception.getCategory()).isEqualTo(ErrorCategory.RETRYABLE_FAILURE);
        assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(exception.getCode()).isEqualTo(409);
        assertThat(exception.isRetryable()).isTrue();
        assertThat(exception.isAlertRequired()).isFalse();
    }

    @Test
    void invalidArgumentException_shouldMapToInvalidArgumentCategory() {
        InvalidArgumentException exception = new InvalidArgumentException("参数不能为空");

        assertThat(exception.getCategory()).isEqualTo(ErrorCategory.INVALID_ARGUMENT);
        assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(exception.getCode()).isEqualTo(400);
        assertThat(exception.getUserMessage()).isEqualTo("参数不能为空");
        assertThat(exception.isRetryable()).isFalse();
        assertThat(exception.isIgnorable()).isFalse();
    }

    @Test
    void ignorableFailureException_shouldMapToIgnorableCategory() {
        IgnorableFailureException exception = new IgnorableFailureException("无风险项可跳过");

        assertThat(exception.getCategory()).isEqualTo(ErrorCategory.IGNORABLE_FAILURE);
        assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.OK);
        assertThat(exception.getCode()).isEqualTo(200);
        assertThat(exception.isRetryable()).isFalse();
        assertThat(exception.isIgnorable()).isTrue();
        assertThat(exception.isAlertRequired()).isFalse();
    }

    @Test
    void externalDependencyFailureException_shouldMapToExternalDependencyCategory() {
        ExternalDependencyFailureException exception = new ExternalDependencyFailureException("第三方服务不可用");

        assertThat(exception.getCategory()).isEqualTo(ErrorCategory.EXTERNAL_DEPENDENCY_FAILURE);
        assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(exception.getCode()).isEqualTo(502);
        assertThat(exception.isRetryable()).isTrue();
        assertThat(exception.isIgnorable()).isFalse();
    }

    @Test
    void alertRequiredFailureException_shouldMapToAlertRequiredCategory() {
        AlertRequiredFailureException exception = new AlertRequiredFailureException(
                500, HttpStatus.INTERNAL_SERVER_ERROR, "系统异常，请联系管理员");

        assertThat(exception.getCategory()).isEqualTo(ErrorCategory.ALERT_REQUIRED_FAILURE);
        assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(exception.getCode()).isEqualTo(500);
        assertThat(exception.isAlertRequired()).isTrue();
        assertThat(exception.isRetryable()).isFalse();
    }
}
