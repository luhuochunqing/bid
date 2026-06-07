package com.xiyu.bid.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class UpdateRoleRequest {

    @NotBlank(message = "Role name is required")
    @Size(max = 100, message = "Role name must not exceed 100 characters")
    private String name;

    @Size(max = 255, message = "Role description must not exceed 255 characters")
    private String description;

    private String dataScope = "self";

    private Boolean enabled = true;

    private List<String> menuPermissions = new ArrayList<>();

    private List<Long> allowedProjects = new ArrayList<>();

    private List<String> allowedDepts = new ArrayList<>();
}
