package com.xiyu.bid.notification.outbound.service;

public record NotificationDeliveryResult(
        boolean successful,
        boolean skipped,
        Integer errcode,
        String message
) {
    public static NotificationDeliveryResult success(Integer errcode, String message) {
        return new NotificationDeliveryResult(true, false, errcode, message);
    }

    public static NotificationDeliveryResult skip(String message) {
        return new NotificationDeliveryResult(true, true, null, message);
    }

    public static NotificationDeliveryResult failure(Integer errcode, String message) {
        return new NotificationDeliveryResult(false, false, errcode, message);
    }
}
