package com.xiyu.bid.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UserOrganizationUpdateRequest {
    @NotBlank(message = "Department code is required")
    private String departmentCode;

    @NotNull(message = "Role is required")
    private Long roleId;

    private Boolean enabled = true;
}
