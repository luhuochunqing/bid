package com.xiyu.bid.notification.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationDispatchPolicyTest {

    @Test
    void validateDispatch_ShouldRejectNullUserId() {
        DispatchResult result =
            NotificationDispatchPolicy.validateDispatch(NotificationType.INFO, null, "Hello", "Body");

        assertThat(result.isValid()).isFalse();
        assertThat(result.errorCode()).isEqualTo("INVALID_USER");
    }

    @Test
    void validateDispatch_ShouldRejectNullType() {
        DispatchResult result =
            NotificationDispatchPolicy.validateDispatch(null, 1L, "Hello", "Body");

        assertThat(result.isValid()).isFalse();
        assertThat(result.errorCode()).isEqualTo("INVALID_TYPE");
    }

    @Test
    void validateDispatch_ShouldAcceptValidInputs() {
        DispatchResult result =
            NotificationDispatchPolicy.validateDispatch(NotificationType.INFO, 1L, "Hello", "Body");

        assertThat(result.isValid()).isTrue();
        assertThat(result.errorCode()).isNull();
        assertThat(result.errorMessage()).isNull();
    }

    @Test
    void validateDispatch_ShouldRejectEmptyTitle() {
        DispatchResult resultBlank =
            NotificationDispatchPolicy.validateDispatch(NotificationType.INFO, 1L, "   ", "Body");
        DispatchResult resultNull =
            NotificationDispatchPolicy.validateDispatch(NotificationType.INFO, 1L, null, "Body");

        assertThat(resultBlank.isValid()).isFalse();
        assertThat(resultBlank.errorCode()).isEqualTo("INVALID_TITLE");
        assertThat(resultNull.isValid()).isFalse();
        assertThat(resultNull.errorCode()).isEqualTo("INVALID_TITLE");
    }

    @Test
    void validateDispatch_ShouldRejectTitleLongerThan200Chars() {
        String longTitle = "a".repeat(201);

        DispatchResult result =
            NotificationDispatchPolicy.validateDispatch(NotificationType.INFO, 1L, longTitle, "Body");

        assertThat(result.isValid()).isFalse();
        assertThat(result.errorCode()).isEqualTo("TITLE_TOO_LONG");
    }
}
