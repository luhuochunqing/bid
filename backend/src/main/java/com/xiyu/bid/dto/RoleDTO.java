package com.xiyu.bid.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoleDTO {

    private Long id;
    private String code;
    private String name;
    private String description;
    private Boolean system;
    private Boolean enabled;
    private Integer userCount;
    private String dataScope;
    @Builder.Default
    private List<String> menuPermissions = List.of();
    @Builder.Default
    private List<Long> allowedProjects = List.of();
    @Builder.Default
    private List<String> allowedDepts = List.of();
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
