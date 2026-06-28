package com.xiyu.bid.dto;

import com.xiyu.bid.annotation.Sensitive;
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

    @Sensitive
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
        // SAFE: 登录响应构造路径。这里是 fallback 构造器（其他重载用更完整字段），实际登录主路径
        // 调用下方的"完整构造方法"传入由 DataScopeConfigService.getRoleCode(user) 解析的 roleCode。
        // 此重载目前仅在测试 / 历史调用点使用，最终登录响应以"完整构造方法"为准。CO-373 治理范围。
        return from(token, user, List.of(), List.of(), List.of(),
                // SAFE: 同上，登录响应 fallback 构造器（CO-373 治理范围外的兼容性保留）。
                user.getRoleCode(), user.getRoleName());
    }

    public static AuthResponse from(String token, User user, List<Long> allowedProjectIds) {
        // SAFE: 登录响应构造路径（仅传 allowedProjectIds 的中间重载）。当前未被主流程调用，保留向后兼容。
        return from(token, user, allowedProjectIds, List.of(), List.of(),
                // SAFE: 同上，登录响应兼容性重载（CO-373 治理范围外）。
                user.getRoleCode(), user.getRoleName());
    }

    public static AuthResponse from(String token, User user, List<Long> allowedProjectIds, List<String> allowedDepts) {
        // SAFE: 登录响应构造路径（仅传 allowedDepts 的中间重载）。当前未被主流程调用，保留向后兼容。
        return from(token, user, allowedProjectIds, allowedDepts, List.of(),
                // SAFE: 同上，登录响应兼容性重载（CO-373 治理范围外）。
                user.getRoleCode(), user.getRoleName());
    }

    public static AuthResponse from(
            String token,
            User user,
            List<Long> allowedProjectIds,
            List<String> allowedDepts,
            List<String> menuPermissions
    ) {
        // SAFE: 登录响应构造路径。当前 AuthService/AuthController 仍以这个重载为兼容入口，
        // 调用方在传 user 之前会先确保登录账号类型（OSS/本地）并走对应权限解析路径。
        // 真正登录主路径（OAuth2SuccessHandler / AuthController.refreshToken）走下方"完整构造方法"
        // 显式传入 dataScopeConfigService.getRoleCode(user) 结果。CO-373 治理范围。
        return from(token, user, allowedProjectIds, allowedDepts, menuPermissions,
                // SAFE: 同上，登录响应兼容性主入口（CO-373 治理范围外）。
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
