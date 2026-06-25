package com.xiyu.bid.task.core;

public record TaskOperationDecision(boolean allowed, String reason) {

    public static TaskOperationDecision permit() {
        return new TaskOperationDecision(true, null);
    }

    public static TaskOperationDecision deny(String reason) {
        return new TaskOperationDecision(false, reason);
    }
}
