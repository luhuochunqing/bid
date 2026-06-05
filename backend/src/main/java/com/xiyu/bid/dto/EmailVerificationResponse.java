package com.xiyu.bid.dto;

/**
 * DTO for email verification response
 */
public record EmailVerificationResponse(
    String message
) {
    public EmailVerificationResponse {
        if (message == null) {
            message = "Verification email sent";
        }
    }
}
