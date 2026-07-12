package com.xiyu.bid.notification.core;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationDedupPolicyTest {

    @Test
    void returnsFalseWhenNoExistingTimestamps() {
        Instant now = Instant.parse("2026-07-12T10:00:00Z");
        assertThat(NotificationDedupPolicy.isDuplicate(now, List.of())).isFalse();
    }

    @Test
    void returnsFalseWhenAllTimestampsAreOutsideWindow() {
        Instant now = Instant.parse("2026-07-12T10:00:00Z");
        List<Instant> existing = List.of(
            Instant.parse("2026-07-12T09:54:59Z")
        );
        assertThat(NotificationDedupPolicy.isDuplicate(now, existing)).isFalse();
    }

    @Test
    void returnsTrueWhenTimestampsAreExactlyAtWindowBoundary() {
        Instant now = Instant.parse("2026-07-12T10:00:00Z");
        List<Instant> existing = List.of(
            Instant.parse("2026-07-12T09:55:00Z")
        );
        assertThat(NotificationDedupPolicy.isDuplicate(now, existing)).isTrue();
    }

    @Test
    void returnsTrueWhenTimestampInsideDefaultWindow() {
        Instant now = Instant.parse("2026-07-12T10:00:00Z");
        List<Instant> existing = List.of(
            Instant.parse("2026-07-12T09:56:00Z")
        );
        assertThat(NotificationDedupPolicy.isDuplicate(now, existing)).isTrue();
    }

    @Test
    void returnsFalseWhenTimestampBeforeCustomWindow() {
        Instant now = Instant.parse("2026-07-12T10:00:00Z");
        List<Instant> existing = List.of(
            Instant.parse("2026-07-12T09:49:59Z")
        );
        assertThat(NotificationDedupPolicy.isDuplicate(now, existing, Duration.ofMinutes(10))).isFalse();
    }

    @Test
    void returnsTrueWhenTimestampInsideCustomWindow() {
        Instant now = Instant.parse("2026-07-12T10:00:00Z");
        List<Instant> existing = List.of(
            Instant.parse("2026-07-12T09:55:01Z")
        );
        assertThat(NotificationDedupPolicy.isDuplicate(now, existing, Duration.ofMinutes(10))).isTrue();
    }

    @Test
    void ignoresNullTimestamps() {
        Instant now = Instant.parse("2026-07-12T10:00:00Z");
        List<Instant> existing = Collections.singletonList(null);
        assertThat(NotificationDedupPolicy.isDuplicate(now, existing)).isFalse();
    }

    @Test
    void returnsTrueWhenAnyTimestampInsideWindow() {
        Instant now = Instant.parse("2026-07-12T10:00:00Z");
        List<Instant> existing = List.of(
            Instant.parse("2026-07-12T09:50:00Z"),
            Instant.parse("2026-07-12T09:58:00Z")
        );
        assertThat(NotificationDedupPolicy.isDuplicate(now, existing)).isTrue();
    }
}
