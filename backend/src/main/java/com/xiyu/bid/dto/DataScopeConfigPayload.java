package com.xiyu.bid.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataScopeConfigPayload {

    @Builder.Default
    private List<DepartmentNode> departmentTree = new ArrayList<>();

    @Builder.Default
    private List<UserScopeRule> userRules = new ArrayList<>();

    @Builder.Default
    private List<DepartmentScopeRule> departmentRules = new ArrayList<>();

    @Builder.Default
    private List<RolePermissionRule> roles = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DepartmentNode {
        private String departmentCode;
        private String departmentName;
        private String parentDepartmentCode;
        private Integer sortOrder;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserScopeRule {
        private Long userId;
        private String dataScope;
        @Builder.Default
        private List<Long> allowedProjectIds = new ArrayList<>();
        @Builder.Default
        private List<String> allowedDeptCodes = new ArrayList<>();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DepartmentScopeRule {
        private String departmentCode;
        private String dataScope;
        private boolean canViewOtherDepts;
        @Builder.Default
        private List<String> allowedDeptCodes = new ArrayList<>();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RolePermissionRule {
        private String code;
        private String dataScope;
        @Builder.Default
        private List<String> menuPermissions = new ArrayList<>();
        @Builder.Default
        private List<Long> allowedProjectIds = new ArrayList<>();
        @Builder.Default
        private List<String> allowedDeptCodes = new ArrayList<>();
    }
}
