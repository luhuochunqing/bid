package com.xiyu.bid.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AdminUserUpdateRequest {

    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
    private String username;

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    private String email;

    @NotBlank(message = "Full name is required")
    @Size(max = 100, message = "Full name must not exceed 100 characters")
    private String fullName;

    @Size(max = 32, message = "Phone must not exceed 32 characters")
    private String phone;

    @Size(max = 100, message = "Department code must not exceed 100 characters")
    private String departmentCode;

    @Size(max = 100, message = "Department name must not exceed 100 characters")
    private String departmentName;

    @NotNull(message = "Role is required")
    private Long roleId;

    private Boolean enabled = true;
}
