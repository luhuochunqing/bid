// Input: notification ownership and existing read timestamp
// Output: ReadResult value (valid/forbidden/idempotent already-read)
// Pos: Pure Core/通知已读策略
package com.xiyu.bid.notification.core;

import java.time.Instant;
import java.util.Objects;

/**
 * Pure validation policy for marking a notification as read.
 *
 * <p>Idempotent: an already-read notification returns {@code valid + alreadyRead}
 * instead of an error. Cross-user reads return a forbidden error code.
 */
public final class NotificationReadPolicy {

    private NotificationReadPolicy() {
    }

    public static ReadResult validateRead(Long notificationUserId, Long requestingUserId, Instant existingReadAt) {
        if (notificationUserId == null || requestingUserId == null
            || !Objects.equals(notificationUserId, requestingUserId)) {
            return ReadResult.forbidden();
        }
        if (existingReadAt != null) {
            return ReadResult.ofAlreadyRead();
        }
        return ReadResult.valid();
    }

    public record ReadResult(boolean isValid, boolean alreadyRead, String errorCode, String errorMessage) {

        public static ReadResult valid() {
            return new ReadResult(true, false, null, null);
        }

        public static ReadResult ofAlreadyRead() {
            return new ReadResult(true, true, null, null);
        }

        public static ReadResult forbidden() {
            return new ReadResult(false, false, "FORBIDDEN", "Notification does not belong to the requesting user");
        }
    }
}
