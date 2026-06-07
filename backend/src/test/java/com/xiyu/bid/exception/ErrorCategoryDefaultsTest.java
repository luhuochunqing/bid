package com.xiyu.bid.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.slf4j.event.Level;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorCategoryDefaultsTest {

    @Test
    void businessUnavailable_shouldHaveCorrectDefaults() {
        var defaults = ErrorCategoryDefaults.BUSINESS_UNAVAILABLE;
        assertThat(defaults.defaultHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(defaults.defaultLogLevel()).isEqualTo(Level.WARN);
        assertThat(defaults.defaultRetryable()).isFalse();
    }

    @Test
    void invalidArgument_shouldHaveCorrectDefaults() {
        var defaults = ErrorCategoryDefaults.INVALID_ARGUMENT;
        assertThat(defaults.defaultHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(defaults.defaultLogLevel()).isEqualTo(Level.WARN);
        assertThat(defaults.defaultRetryable()).isFalse();
    }

    @Test
    void resourceNotFound_shouldHaveCorrectDefaults() {
        var defaults = ErrorCategoryDefaults.RESOURCE_NOT_FOUND;
        assertThat(defaults.defaultHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(defaults.defaultLogLevel()).isEqualTo(Level.WARN);
        assertThat(defaults.defaultRetryable()).isFalse();
    }

    @Test
    void externalDependencyFailure_shouldHaveCorrectDefaults() {
        var defaults = ErrorCategoryDefaults.EXTERNAL_DEPENDENCY_FAILURE;
        assertThat(defaults.defaultHttpStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(defaults.defaultLogLevel()).isEqualTo(Level.ERROR);
        assertThat(defaults.defaultRetryable()).isTrue();
    }

    @Test
    void ignorableFailure_shouldHaveCorrectDefaults() {
        var defaults = ErrorCategoryDefaults.IGNORABLE_FAILURE;
        assertThat(defaults.defaultHttpStatus()).isEqualTo(HttpStatus.OK);
        assertThat(defaults.defaultLogLevel()).isEqualTo(Level.WARN);
        assertThat(defaults.defaultRetryable()).isFalse();
    }

    @Test
    void retryableFailure_shouldHaveCorrectDefaults() {
        var defaults = ErrorCategoryDefaults.RETRYABLE_FAILURE;
        assertThat(defaults.defaultHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(defaults.defaultLogLevel()).isEqualTo(Level.WARN);
        assertThat(defaults.defaultRetryable()).isTrue();
    }

    @Test
    void degraded_shouldHaveCorrectDefaults() {
        var defaults = ErrorCategoryDefaults.DEGRADED;
        assertThat(defaults.defaultHttpStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(defaults.defaultLogLevel()).isEqualTo(Level.WARN);
        assertThat(defaults.defaultRetryable()).isTrue();
    }

    @Test
    void alertRequiredFailure_shouldHaveCorrectDefaults() {
        var defaults = ErrorCategoryDefaults.ALERT_REQUIRED_FAILURE;
        assertThat(defaults.defaultHttpStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(defaults.defaultLogLevel()).isEqualTo(Level.ERROR);
        assertThat(defaults.defaultRetryable()).isFalse();
    }
}
