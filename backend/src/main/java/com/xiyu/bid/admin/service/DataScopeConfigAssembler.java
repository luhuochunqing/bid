package com.xiyu.bid.admin.service;

import com.xiyu.bid.admin.settings.core.DataScopePolicy;
import com.xiyu.bid.admin.settings.core.DepartmentGraph;
import com.xiyu.bid.admin.settings.core.DepartmentGraphPolicy;
import com.xiyu.bid.admin.settings.core.DepartmentNode;
import com.xiyu.bid.admin.settings.core.DepartmentOption;
import com.xiyu.bid.dto.DataScopeConfigPayload;
import com.xiyu.bid.dto.DataScopeConfigResponse;
import com.xiyu.bid.entity.RoleProfile;
import com.xiyu.bid.entity.User;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class DataScopeConfigAssembler {

    public DataScopeConfigResponse toResponse(List<User> users, List<RoleProfile> roles, DataScopeConfigPayload payload) {
        DepartmentGraph graph = DepartmentGraphPolicy.buildGraph(mergeDepartments(users, payload.getDepartmentTree()));
        Map<Long, DataScopeConfigPayload.UserScopeRule> userRules = indexUserRules(payload);
        Map<String, DataScopeConfigPayload.DepartmentScopeRule> deptRules = indexDepartmentRules(payload);
        return DataScopeConfigResponse.builder()
                .userDataScope(users.stream().map(user -> toUserScopeItem(user, userRules.get(user.getId()))).toList())
                .deptDataScope(graph.options().stream().map(option -> toDeptScopeItem(option, deptRules.get(option.code()))).toList())
                .deptOptions(graph.options().stream().map(this::toDeptOptionItem).toList())
                .deptTree(graph.tree().stream().map(this::toDeptTreeItem).toList())
                .userOptions(users.stream().map(this::toUserOptionItem).toList())
                .users(users.stream().map(this::toUserItem).toList())
                .roles(toRoleItems(users, roles))
                .build();
    }

    public DataScopeConfigPayload toPayload(DataScopeConfigResponse request) {
        DataScopeConfigResponse safe = request == null ? DataScopeConfigResponse.builder().build() : request;
        return DataScopeConfigPayload.builder()
                .departmentTree(toPayloadDepartments(safe.getDeptTree()))
                .userRules(toUserRules(safe.getUserDataScope()))
                .departmentRules(toDepartmentRules(safe.getDeptDataScope()))
                .build();
    }

    public List<DepartmentNode> toCoreDepartments(List<DataScopeConfigResponse.DepartmentTreeItem> items) {
        if (items == null) {
            return List.of();
        }
        return items.stream()
                .map(item -> new DepartmentNode(item.getDeptCode(), item.getDeptName(), item.getParentDeptCode(), item.getSortOrder() == null ? 0 : item.getSortOrder()))
                .toList();
    }

    public DataScopeConfigPayload withDepartments(DataScopeConfigPayload payload, List<DepartmentNode> departments) {
        return DataScopeConfigPayload.builder()
                .departmentTree(departments.stream().map(this::toPayloadDepartment).toList())
                .userRules(payload.getUserRules())
                .departmentRules(payload.getDepartmentRules())
                .build();
    }

    public DepartmentGraph buildGraph(List<User> users, DataScopeConfigPayload payload) {
        return DepartmentGraphPolicy.buildGraph(mergeDepartments(users, payload.getDepartmentTree()));
    }

    private List<DepartmentNode> mergeDepartments(List<User> users, List<DataScopeConfigPayload.DepartmentNode> storedTree) {
        Map<String, DepartmentNode> definitions = new LinkedHashMap<>();
        toCoreStoredDepartments(storedTree).forEach(node -> definitions.put(node.code(), node));
        users.forEach(user -> definitions.putIfAbsent(
                DepartmentGraphPolicy.normalizeCode(user.getDepartmentCode()),
                new DepartmentNode(
                        DepartmentGraphPolicy.normalizeCode(user.getDepartmentCode()),
                        DepartmentGraphPolicy.normalizeName(user.getDepartmentName()),
                        null,
                        definitions.size()
                )
        ));
        return List.copyOf(definitions.values());
    }

    private List<DepartmentNode> toCoreStoredDepartments(List<DataScopeConfigPayload.DepartmentNode> items) {
        return items == null ? List.of() : items.stream()
                .map(item -> new DepartmentNode(item.getDepartmentCode(), item.getDepartmentName(), item.getParentDepartmentCode(), item.getSortOrder() == null ? 0 : item.getSortOrder()))
                .toList();
    }

    private Map<Long, DataScopeConfigPayload.UserScopeRule> indexUserRules(DataScopeConfigPayload payload) {
        return payload.getUserRules().stream()
                .filter(rule -> rule.getUserId() != null)
                .collect(Collectors.toMap(DataScopeConfigPayload.UserScopeRule::getUserId, Function.identity(), (left, right) -> right, LinkedHashMap::new));
    }

    private Map<String, DataScopeConfigPayload.DepartmentScopeRule> indexDepartmentRules(DataScopeConfigPayload payload) {
        return payload.getDepartmentRules().stream()
                .filter(rule -> rule.getDepartmentCode() != null && !rule.getDepartmentCode().isBlank())
                .collect(Collectors.toMap(rule -> DepartmentGraphPolicy.normalizeCode(rule.getDepartmentCode()), Function.identity(), (left, right) -> right, LinkedHashMap::new));
    }

    private DataScopeConfigResponse.UserDataScopeItem toUserScopeItem(User user, DataScopeConfigPayload.UserScopeRule rule) {
        return DataScopeConfigResponse.UserDataScopeItem.builder()
                .userId(user.getId()).userName(user.getFullName())
                .deptCode(DepartmentGraphPolicy.normalizeCode(user.getDepartmentCode()))
                .dept(DepartmentGraphPolicy.normalizeName(user.getDepartmentName()))
                .role(user.getRoleCode())
                .dataScope(DataScopePolicy.normalizeScope(rule == null ? null : rule.getDataScope()))
                .allowedProjects(rule == null ? List.of() : DataScopePolicy.normalizeProjectIds(rule.getAllowedProjectIds()))
                .allowedDepts(rule == null ? List.of() : DataScopePolicy.normalizeDeptCodes(rule.getAllowedDeptCodes()))
                .build();
    }

    private DataScopeConfigResponse.DepartmentDataScopeItem toDeptScopeItem(DepartmentOption option, DataScopeConfigPayload.DepartmentScopeRule rule) {
        return DataScopeConfigResponse.DepartmentDataScopeItem.builder()
                .deptCode(option.code()).deptName(option.name())
                .dataScope(DataScopePolicy.normalizeScope(rule == null ? null : rule.getDataScope()))
                .canViewOtherDepts(rule != null && rule.isCanViewOtherDepts())
                .allowedDepts(rule == null ? List.of() : DataScopePolicy.normalizeDeptCodes(rule.getAllowedDeptCodes()))
                .build();
    }

    private List<DataScopeConfigResponse.RolePermissionItem> toRoleItems(List<User> users, List<RoleProfile> roles) {
        Map<String, Integer> countByRole = users.stream().collect(Collectors.groupingBy(User::getRoleCode, LinkedHashMap::new, Collectors.collectingAndThen(Collectors.counting(), Long::intValue)));
        return roles.stream()
                .sorted(Comparator.comparing(RoleProfile::getIsSystem).reversed().thenComparing(RoleProfile::getCode, String.CASE_INSENSITIVE_ORDER))
                .map(role -> DataScopeConfigResponse.RolePermissionItem.builder()
                        .id(role.getId()).code(normalizeRoleCode(role.getCode())).name(role.getName()).description(role.getDescription())
                        .system(Boolean.TRUE.equals(role.getIsSystem())).enabled(Boolean.TRUE.equals(role.getEnabled()))
                        .userCount(countByRole.getOrDefault(normalizeRoleCode(role.getCode()), 0))
                        .dataScope(DataScopePolicy.normalizeScope(role.getDataScope()))
                        .menuPermissions(role.getMenuPermissions())
                        .allowedProjects(DataScopePolicy.normalizeProjectIds(role.getAllowedProjects()))
                        .allowedDepts(DataScopePolicy.normalizeDeptCodes(role.getAllowedDepts()))
                        .build())
                .toList();
    }

    private DataScopeConfigResponse.DepartmentOptionItem toDeptOptionItem(DepartmentOption option) {
        return DataScopeConfigResponse.DepartmentOptionItem.builder().code(option.code()).name(option.name()).build();
    }

    private DataScopeConfigResponse.DepartmentTreeItem toDeptTreeItem(DepartmentNode node) {
        return DataScopeConfigResponse.DepartmentTreeItem.builder().deptCode(node.code()).deptName(node.name()).parentDeptCode(node.parentCode()).sortOrder(node.sortOrder()).build();
    }

    private DataScopeConfigResponse.UserOptionItem toUserOptionItem(User user) {
        Long roleId = user.getRoleProfile() == null ? null : user.getRoleProfile().getId();
        return DataScopeConfigResponse.UserOptionItem.builder()
                .id(user.getId())
                .name(user.getFullName())
                .roleId(roleId)
                .role(user.getRoleCode())
                .roleName(user.getRoleName())
                .deptCode(DepartmentGraphPolicy.normalizeCode(user.getDepartmentCode()))
                .dept(DepartmentGraphPolicy.normalizeName(user.getDepartmentName()))
                .build();
    }

    private DataScopeConfigResponse.UserItem toUserItem(User user) {
        return DataScopeConfigResponse.UserItem.builder().id(user.getId()).username(user.getUsername()).fullName(user.getFullName()).email(user.getEmail()).phone(user.getPhone()).departmentCode(DepartmentGraphPolicy.normalizeCode(user.getDepartmentCode())).departmentName(DepartmentGraphPolicy.normalizeName(user.getDepartmentName())).roleId(user.getRoleProfile() == null ? null : user.getRoleProfile().getId()).role(user.getRoleCode()).roleName(user.getRoleName()).enabled(Boolean.TRUE.equals(user.getEnabled())).build();
    }

    private List<DataScopeConfigPayload.DepartmentNode> toPayloadDepartments(List<DataScopeConfigResponse.DepartmentTreeItem> items) {
        return DepartmentGraphPolicy.normalizeTree(toCoreDepartments(items)).stream().map(this::toPayloadDepartment).toList();
    }

    private DataScopeConfigPayload.DepartmentNode toPayloadDepartment(DepartmentNode node) {
        return DataScopeConfigPayload.DepartmentNode.builder().departmentCode(node.code()).departmentName(node.name()).parentDepartmentCode(node.parentCode()).sortOrder(node.sortOrder()).build();
    }

    private List<DataScopeConfigPayload.UserScopeRule> toUserRules(List<DataScopeConfigResponse.UserDataScopeItem> items) {
        return items == null ? List.of() : items.stream().filter(item -> item.getUserId() != null).map(item -> DataScopeConfigPayload.UserScopeRule.builder().userId(item.getUserId()).dataScope(DataScopePolicy.normalizeScope(item.getDataScope())).allowedProjectIds(DataScopePolicy.normalizeProjectIds(item.getAllowedProjects())).allowedDeptCodes(DataScopePolicy.normalizeDeptCodes(item.getAllowedDepts())).build()).toList();
    }

    private List<DataScopeConfigPayload.DepartmentScopeRule> toDepartmentRules(List<DataScopeConfigResponse.DepartmentDataScopeItem> items) {
        return items == null ? List.of() : items.stream().filter(item -> item.getDeptCode() != null && !item.getDeptCode().isBlank()).map(item -> DataScopeConfigPayload.DepartmentScopeRule.builder().departmentCode(DepartmentGraphPolicy.normalizeCode(item.getDeptCode())).dataScope(DataScopePolicy.normalizeScope(item.getDataScope())).canViewOtherDepts(item.isCanViewOtherDepts()).allowedDeptCodes(DataScopePolicy.normalizeDeptCodes(item.getAllowedDepts())).build()).toList();
    }

    private String normalizeRoleCode(String roleCode) {
        return roleCode == null ? "" : roleCode.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
