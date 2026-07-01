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

    @Size(max = 32, message = "Employee number must not exceed 32 characters")
    private String employeeNumber;

    /** CRM 工号（CO-152）：配置后该用户使用专属 CRM JWT token，null 时回退全局共享 */
    @Size(max = 64, message = "CRM sales number must not exceed 64 characters")
    private String crmSalesNo;

    @NotNull(message = "Role is required")
    private Long roleId;

    private Boolean enabled = true;
}
