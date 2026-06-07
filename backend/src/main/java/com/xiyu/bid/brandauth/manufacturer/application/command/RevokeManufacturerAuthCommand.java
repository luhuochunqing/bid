package com.xiyu.bid.brandauth.manufacturer.application.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RevokeManufacturerAuthCommand(
        @NotBlank @Size(min = MIN_LEN, message = "作废原因不能少于10个字")
        String reason
) {
    /** Minimum character count for revoke reason. */
    private static final int MIN_LEN = 10;
}
