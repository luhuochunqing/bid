package com.xiyu.bid.notification.core;

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
