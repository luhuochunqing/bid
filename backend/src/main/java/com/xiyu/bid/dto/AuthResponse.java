package com.xiyu.bid.dto;

import com.xiyu.bid.entity.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {

    private String token;
    private String type = "Bearer";
    private Long id;
    private String username;
    private String email;
    private String fullName;
    private String role;
    private String roleCode;
    private String roleName;
    private String deptCode;
    private String dept;
    @Builder.Default
    private List<Long> allowedProjectIds = List.of();
    @Builder.Default
    private List<String> allowedDepts = List.of();
    @Builder.Default
    private List<String> menuPermissions = List.of();

    public static AuthResponse from(String token, User user) {
        return from(token, user, List.of(), List.of(), List.of());
    }

    public static AuthResponse from(String token, User user, List<Long> allowedProjectIds) {
        return from(token, user, allowedProjectIds, List.of(), List.of());
    }

    public static AuthResponse from(String token, User user, List<Long> allowedProjectIds, List<String> allowedDepts) {
        return from(token, user, allowedProjectIds, allowedDepts, List.of());
    }

    public static AuthResponse from(
            String token,
            User user,
            List<Long> allowedProjectIds,
            List<String> allowedDepts,
            List<String> menuPermissions
    ) {
        return AuthResponse.builder()
                .token(token)
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(user.getRoleCode())
                .roleCode(user.getRoleCode())
                .roleName(user.getRoleName())
                .deptCode(user.getDepartmentCode())
                .dept(user.getDepartmentName())
                .allowedProjectIds(allowedProjectIds == null ? List.of() : allowedProjectIds)
                .allowedDepts(allowedDepts == null ? List.of() : allowedDepts)
                .menuPermissions(menuPermissions == null ? List.of() : menuPermissions)
                .build();
    }
}
