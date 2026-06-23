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
    @Builder.Default
    private String type = "Bearer";
    private Long id;
    private String username;
    private String email;
    private String phone;
    private String fullName;
    private String employeeNumber;
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
        return from(token, user, List.of(), List.of(), List.of(),
                user.getRoleCode(), user.getRoleName());
    }

    public static AuthResponse from(String token, User user, List<Long> allowedProjectIds) {
        return from(token, user, allowedProjectIds, List.of(), List.of(),
                user.getRoleCode(), user.getRoleName());
    }

    public static AuthResponse from(String token, User user, List<Long> allowedProjectIds, List<String> allowedDepts) {
        return from(token, user, allowedProjectIds, allowedDepts, List.of(),
                user.getRoleCode(), user.getRoleName());
    }

    public static AuthResponse from(
            String token,
            User user,
            List<Long> allowedProjectIds,
            List<String> allowedDepts,
            List<String> menuPermissions
    ) {
        return from(token, user, allowedProjectIds, allowedDepts, menuPermissions,
                user.getRoleCode(), user.getRoleName());
    }

    /**
     * 完整构造方法：显式传入 roleCode 和 roleName，用于优先从 OSS 权限缓存读取的场景。
     * <p>
     * 当登录/刷新 token 时，应通过 {@code dataScopeConfigService.getRoleCode(user)} 和
     * {@code dataScopeConfigService.getRoleName(user)} 传入 OSS 实时抓取的角色信息，
     * 避免直接读取本地 DB 的 user.getRoleCode()/getRoleName()。
     */
    public static AuthResponse from(
            String token,
            User user,
            List<Long> allowedProjectIds,
            List<String> allowedDepts,
            List<String> menuPermissions,
            String roleCode,
            String roleName
    ) {
        return AuthResponse.builder()
                .token(token)
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .phone(user.getPhone())
                .fullName(user.getFullName())
                .employeeNumber(user.getEmployeeNumber())
                .role(roleCode)
                .roleCode(roleCode)
                .roleName(roleName)
                .deptCode(user.getDepartmentCode())
                .dept(user.getDepartmentName())
                .allowedProjectIds(allowedProjectIds == null ? List.of() : allowedProjectIds)
                .allowedDepts(allowedDepts == null ? List.of() : allowedDepts)
                .menuPermissions(menuPermissions == null ? List.of() : menuPermissions)
                .build();
    }
}
