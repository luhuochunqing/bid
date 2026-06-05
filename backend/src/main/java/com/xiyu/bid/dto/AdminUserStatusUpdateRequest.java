package com.xiyu.bid.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AdminUserStatusUpdateRequest {

    @NotNull(message = "Enabled flag is required")
    private Boolean enabled;
}
