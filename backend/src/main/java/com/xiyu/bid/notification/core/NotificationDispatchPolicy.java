// Input: dispatch parameters (type, recipient, title, body)
// Output: DispatchResult value indicating validity or error code
// Pos: Pure Core/通知派发策略
package com.xiyu.bid.notification.core;

/**
 * Pure validation policy for notification dispatch.
 *
 * <p>Returns business validation outcomes as values; never throws.
 */
public final class NotificationDispatchPolicy {

    private static final int MAX_TITLE_LENGTH = 200;
    private static final int MAX_BODY_LENGTH = 10_000;
    private static final int MAX_PAYLOAD_KEYS = 50;

    private NotificationDispatchPolicy() {
    }

    public static DispatchResult validateDispatch(NotificationType type, Long userId, String title, String body) {
        return validateDispatch(type, userId, title, body, 0);
    }

    public static DispatchResult validateDispatch(
        NotificationType type, Long userId, String title, String body, int payloadKeyCount) {
        if (type == null) {
            return DispatchResult.invalid("INVALID_TYPE", "Notification type must not be null");
        }
        if (userId == null) {
            return DispatchResult.invalid("INVALID_USER", "Recipient user id must not be null");
        }
        if (title == null || title.isBlank()) {
            return DispatchResult.invalid("INVALID_TITLE", "Notification title must not be blank");
        }
        if (title.length() > MAX_TITLE_LENGTH) {
            return DispatchResult.invalid("TITLE_TOO_LONG",
                "Notification title must be at most " + MAX_TITLE_LENGTH + " characters");
        }
        if (body != null && body.length() > MAX_BODY_LENGTH) {
            return DispatchResult.invalid("BODY_TOO_LONG",
                "Notification body must be at most " + MAX_BODY_LENGTH + " characters");
        }
        if (payloadKeyCount > MAX_PAYLOAD_KEYS) {
            return DispatchResult.invalid("PAYLOAD_TOO_LARGE",
                "Notification payload must have at most " + MAX_PAYLOAD_KEYS + " keys");
        }
        return DispatchResult.valid();
    }

    public record DispatchResult(boolean isValid, String errorCode, String errorMessage, Long notificationId) {

        public static DispatchResult valid() {
            return new DispatchResult(true, null, null, null);
        }

        public static DispatchResult validWithId(Long notificationId) {
            return new DispatchResult(true, null, null, notificationId);
        }

        public static DispatchResult invalid(String errorCode, String errorMessage) {
            return new DispatchResult(false, errorCode, errorMessage, null);
        }
    }
}
