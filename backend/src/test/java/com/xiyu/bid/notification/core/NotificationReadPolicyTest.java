package com.xiyu.bid.notification.core;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationReadPolicyTest {

    @Test
    void validateRead_ShouldRejectWhenNotificationDoesNotBelongToRequestingUser() {
        NotificationReadPolicy.ReadResult result =
            NotificationReadPolicy.validateRead(1L, 2L, null);

        assertThat(result.isValid()).isFalse();
        assertThat(result.alreadyRead()).isFalse();
        assertThat(result.errorCode()).isEqualTo("FORBIDDEN");
    }

    @Test
    void validateRead_ShouldReturnAlreadyReadWhenReadAtAlreadySet() {
        Instant existingReadAt = Instant.parse("2026-04-28T10:00:00Z");

        NotificationReadPolicy.ReadResult result =
            NotificationReadPolicy.validateRead(1L, 1L, existingReadAt);

        assertThat(result.isValid()).isTrue();
        assertThat(result.alreadyRead()).isTrue();
        assertThat(result.errorCode()).isNull();
        assertThat(result.errorMessage()).isNull();
    }

    @Test
    void validateRead_ShouldReturnValidWhenUnread() {
        NotificationReadPolicy.ReadResult result =
            NotificationReadPolicy.validateRead(1L, 1L, null);

        assertThat(result.isValid()).isTrue();
        assertThat(result.alreadyRead()).isFalse();
        assertThat(result.errorCode()).isNull();
    }

    @Test
    void validateRead_ShouldRejectWhenIdsAreNull() {
        NotificationReadPolicy.ReadResult resultOwnerNull =
            NotificationReadPolicy.validateRead(null, 1L, null);
        NotificationReadPolicy.ReadResult resultRequesterNull =
            NotificationReadPolicy.validateRead(1L, null, null);

        assertThat(resultOwnerNull.isValid()).isFalse();
        assertThat(resultOwnerNull.errorCode()).isEqualTo("FORBIDDEN");
        assertThat(resultRequesterNull.isValid()).isFalse();
        assertThat(resultRequesterNull.errorCode()).isEqualTo("FORBIDDEN");
    }
}
