package com.xiyu.bid.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateRoleStatusRequest {

    @NotNull(message = "Enabled status is required")
    private Boolean enabled;
}
