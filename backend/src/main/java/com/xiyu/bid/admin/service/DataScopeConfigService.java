package com.xiyu.bid.admin.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.admin.settings.core.CoreAccessProfile;
import com.xiyu.bid.admin.settings.core.DataScopePolicy;
import com.xiyu.bid.admin.settings.core.DepartmentGraph;
import com.xiyu.bid.admin.settings.core.DepartmentGraphPolicy;
import com.xiyu.bid.admin.settings.core.DepartmentNode;
import com.xiyu.bid.admin.settings.core.DepartmentScopeRule;
import com.xiyu.bid.admin.settings.core.OrganizationValidationResult;
import com.xiyu.bid.admin.settings.core.RoleAccessRule;
import com.xiyu.bid.admin.settings.core.UserAccessSubject;
import com.xiyu.bid.admin.settings.core.UserScopeRule;
import com.xiyu.bid.dto.DataScopeConfigPayload;
import com.xiyu.bid.dto.DataScopeConfigResponse;
import com.xiyu.bid.entity.RoleProfile;
import com.xiyu.bid.entity.RoleProfileCatalog;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.RoleProfileRepository;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.roleprofile.RoleProfileBootstrap;
import com.xiyu.bid.settings.repository.SystemSettingRepository;
import lombok.Builder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class DataScopeConfigService {

    public static final String DATA_SCOPE_CONFIG_KEY = DataScopeConfigStore.DATA_SCOPE_CONFIG_KEY;

    private final DataScopeConfigStore configStore;
    private final DataScopeConfigAssembler assembler;
    private final UserRepository userRepository;
    private final RoleProfileRepository roleProfileRepository;
    private final RoleProfileBootstrap roleProfileBootstrap;

    // Manual constructor: encapsulates Store/Assembler as implementation details
    // so Spring only sees the thin application-service surface.
    @Autowired
    public DataScopeConfigService(
            SystemSettingRepository pSystemSettingRepository,
            UserRepository pUserRepository,
            RoleProfileRepository pRoleProfileRepository,
            RoleProfileBootstrap pRoleProfileBootstrap,
            ObjectMapper objectMapper
    ) {
        this(new DataScopeConfigStore(pSystemSettingRepository, objectMapper),
                new DataScopeConfigAssembler(),
                pUserRepository,
                pRoleProfileRepository,
                pRoleProfileBootstrap);
    }

    DataScopeConfigService(
            DataScopeConfigStore pConfigStore,
            DataScopeConfigAssembler pAssembler,
            UserRepository pUserRepository,
            RoleProfileRepository pRoleProfileRepository,
            RoleProfileBootstrap pRoleProfileBootstrap
    ) {
        this.configStore = pConfigStore;
        this.assembler = pAssembler;
        this.userRepository = pUserRepository;
        this.roleProfileRepository = pRoleProfileRepository;
        this.roleProfileBootstrap = pRoleProfileBootstrap;
    }

    @Transactional
    public DataScopeConfigResponse getConfig() {
        roleProfileBootstrap.ensureSystemRoles();
        return assembler.toResponse(loadUsers(), roleProfileRepository.findAll(), configStore.loadPayload());
    }

    @Transactional
    public DataScopeConfigResponse saveConfig(DataScopeConfigResponse request) {
        DataScopeConfigPayload payload = assembler.toPayload(request);
        validate(DepartmentGraphPolicy.validateTree(assembler.toCoreDepartments(request == null ? List.of() : request.getDeptTree())));
        configStore.savePayload(payload);
        return getConfig();
    }

    @Transactional
    public DataScopeConfigResponse saveDepartments(List<DataScopeConfigResponse.DepartmentTreeItem> deptTree) {
        List<User> users = loadUsers();
        List<DepartmentNode> departments = assembler.toCoreDepartments(deptTree);
        validate(DepartmentGraphPolicy.validateTree(departments));
        List<String> removedBoundDepts = DepartmentGraphPolicy.findRemovedBoundDepartments(departments, assignedDepartmentCodes(users));
        if (!removedBoundDepts.isEmpty()) {
            throw new IllegalArgumentException("部门已绑定用户，不能删除: " + String.join(",", removedBoundDepts));
        }
        DataScopeConfigPayload payload = assembler.withDepartments(configStore.loadPayload(), DepartmentGraphPolicy.normalizeTree(departments));
        configStore.savePayload(payload);
        return getConfig();
    }

    public AccessProfile getAccessProfile(User user) {
        if (user == null) {
            return AccessProfile.empty();
        }
        List<User> users = loadUsers();
        DataScopeConfigPayload payload = configStore.loadPayload();
        DepartmentGraph graph = assembler.buildGraph(users, payload);
        CoreAccessProfile profile = DataScopePolicy.resolveAccessProfile(
                new UserAccessSubject(user.getId(), user.getDepartmentCode()),
                toCoreUserRules(payload.getUserRules()),
                toCoreDepartmentRules(payload.getDepartmentRules()),
                toRoleAccessRule(resolveRoleProfile(user)),
                graph
        );
        return AccessProfile.builder()
                .dataScope(profile.dataScope())
                .explicitProjectIds(profile.explicitProjectIds())
                .allowedDepartmentCodes(profile.allowedDepartmentCodes())
                .build();
    }

    public List<String> getRoleMenuPermissions(User user) {
        return normalizeMenuPermissions(resolveRoleProfile(user).getMenuPermissions());
    }

    public DepartmentGraph getDepartmentGraph() {
        return assembler.buildGraph(loadUsers(), configStore.loadPayload());
    }

    private List<User> loadUsers() {
        return userRepository.findAll().stream()
                .sorted(Comparator.comparing(User::getUsername, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private Set<String> assignedDepartmentCodes(List<User> users) {
        return users.stream()
                .filter(user -> Boolean.TRUE.equals(user.getEnabled()))
                .map(User::getDepartmentCode)
                .filter(code -> code != null && !code.isBlank())
                .map(DepartmentGraphPolicy::normalizeCode)
                .collect(java.util.stream.Collectors.toSet());
    }

    private List<UserScopeRule> toCoreUserRules(List<DataScopeConfigPayload.UserScopeRule> rules) {
        return rules == null ? List.of() : rules.stream()
                .map(rule -> new UserScopeRule(rule.getUserId(), rule.getDataScope(), rule.getAllowedProjectIds(), rule.getAllowedDeptCodes()))
                .toList();
    }

    private List<DepartmentScopeRule> toCoreDepartmentRules(List<DataScopeConfigPayload.DepartmentScopeRule> rules) {
        return rules == null ? List.of() : rules.stream()
                .map(rule -> new DepartmentScopeRule(rule.getDepartmentCode(), rule.getDataScope(), rule.getAllowedDeptCodes()))
                .toList();
    }

    private RoleAccessRule toRoleAccessRule(RoleProfile roleProfile) {
        return new RoleAccessRule(roleProfile.getDataScope(), roleProfile.getAllowedProjects(), roleProfile.getAllowedDepts());
    }

    private RoleProfile resolveRoleProfile(User user) {
        String roleCode = user == null ? null : user.getRoleCode();
        Optional<RoleProfile> roleProfile = roleProfileRepository.findByCodeIgnoreCase(roleCode);
        if (roleProfile.isPresent()) {
            return roleProfile.get();
        }
        RoleProfileCatalog.SeedDefinition definition = RoleProfileCatalog.definitionForCode(roleCode);
        RoleProfile fallbackRole = RoleProfile.builder()
                .code(definition.code())
                .name(definition.name())
                .description(definition.description())
                .isSystem(definition.system())
                .enabled(true)
                .dataScope(definition.dataScope())
                .build();
        fallbackRole.setMenuPermissions(definition.menuPermissions());
        return fallbackRole;
    }

    private List<String> normalizeMenuPermissions(List<String> menuPermissions) {
        return menuPermissions == null ? List.of() : menuPermissions.stream()
                .filter(permission -> permission != null && !permission.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }

    private void validate(OrganizationValidationResult result) {
        if (!result.valid()) {
            throw new IllegalArgumentException(result.message());
        }
    }

    @Builder
    public static class AccessProfile {
        @Builder.Default
        private String dataScope = "self";
        @Builder.Default
        private List<Long> explicitProjectIds = List.of();
        @Builder.Default
        private List<String> allowedDepartmentCodes = List.of();

        public static AccessProfile empty() {
            return AccessProfile.builder().build();
        }

        public String getDataScope() {
            return dataScope;
        }

        public List<Long> getExplicitProjectIds() {
            return explicitProjectIds == null ? List.of() : explicitProjectIds;
        }

        public List<String> getAllowedDepartmentCodes() {
            return allowedDepartmentCodes == null ? List.of() : allowedDepartmentCodes;
        }
    }
}
