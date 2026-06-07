package com.xiyu.bid.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class CreateRoleRequest {

    private static final int MAX_CODE_LENGTH = 64;
    private static final int MAX_NAME_LENGTH = 100;
    private static final int MAX_DESC_LENGTH = 255;

    @NotBlank(message = "Role code is required")
    @Size(max = MAX_CODE_LENGTH, message = "Role code must not exceed 64 characters")
    private String code;

    @NotBlank(message = "Role name is required")
    @Size(max = MAX_NAME_LENGTH, message = "Role name must not exceed 100 characters")
    private String name;

    @Size(max = MAX_DESC_LENGTH, message = "Role description must not exceed 255 characters")
    private String description;

    private String dataScope = "self";

    private Boolean enabled = true;

    private List<String> menuPermissions = new ArrayList<>();

    private List<Long> allowedProjects = new ArrayList<>();

    private List<String> allowedDepts = new ArrayList<>();
}
