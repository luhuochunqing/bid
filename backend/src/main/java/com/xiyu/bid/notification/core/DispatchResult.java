package com.xiyu.bid.notification.core;

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
