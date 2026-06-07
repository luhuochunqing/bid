package com.xiyu.bid.integration.dto;

/**
 * Optional request body for the send-test endpoint.
 * All fields are nullable — content defaults to a standard test message when null.
 */
public record WeComSendTestRequest(
        String content
) {
}
