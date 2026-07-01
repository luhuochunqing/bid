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
import com.xiyu.bid.crm.application.OssPermissionCache;
import com.xiyu.bid.dto.DataScopeConfigPayload;
import com.xiyu.bid.dto.DataScopeConfigResponse;
import com.xiyu.bid.entity.RoleProfile;
import com.xiyu.bid.entity.RoleProfileCatalog;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.RoleProfileRepository;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.roleprofile.RoleProfileBootstrap;
import com.xiyu.bid.settings.repository.SystemSettingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(DataScopeConfigService.class);

    public static final String DATA_SCOPE_CONFIG_KEY = DataScopeConfigStore.DATA_SCOPE_CONFIG_KEY;

    private final DataScopeConfigStore configStore;
    private final DataScopeConfigAssembler assembler;
    private final UserRepository userRepository;
    private final RoleProfileRepository roleProfileRepository;
    private final RoleProfileBootstrap roleProfileBootstrap;
    private final OssPermissionCache ossPermissionCache;

    // Manual constructor: encapsulates Store/Assembler as implementation details
    // so Spring only sees the thin application-service surface.
    @Autowired
    public DataScopeConfigService(
            SystemSettingRepository pSystemSettingRepository,
            UserRepository pUserRepository,
            RoleProfileRepository pRoleProfileRepository,
            RoleProfileBootstrap pRoleProfileBootstrap,
            ObjectMapper objectMapper,
            OssPermissionCache pOssPermissionCache
    ) {
        this(new DataScopeConfigStore(pSystemSettingRepository, objectMapper),
                new DataScopeConfigAssembler(),
                pUserRepository,
                pRoleProfileRepository,
                pRoleProfileBootstrap,
                pOssPermissionCache);
    }

    DataScopeConfigService(
            DataScopeConfigStore pConfigStore,
            DataScopeConfigAssembler pAssembler,
            UserRepository pUserRepository,
            RoleProfileRepository pRoleProfileRepository,
            RoleProfileBootstrap pRoleProfileBootstrap,
            OssPermissionCache pOssPermissionCache
    ) {
        this.configStore = pConfigStore;
        this.assembler = pAssembler;
        this.userRepository = pUserRepository;
        this.roleProfileRepository = pRoleProfileRepository;
        this.roleProfileBootstrap = pRoleProfileBootstrap;
        this.ossPermissionCache = pOssPermissionCache;
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

    public DataScopeAccessProfile getAccessProfile(User user) {
        if (user == null) {
            return DataScopeAccessProfile.empty();
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
        return DataScopeAccessProfile.builder()
                .dataScope(profile.dataScope())
                .explicitProjectIds(profile.explicitProjectIds())
                .allowedDepartmentCodes(profile.allowedDepartmentCodes())
                .build();
    }

    public List<String> getRoleMenuPermissions(User user) {
        if (user == null) return List.of();
        Optional<List<String>> cachedPermissions = ossPermissionCache.getMenuPermissions(user.getUsername());
        if (cachedPermissions.isPresent()) {
            List<String> ossPermissions = normalizeMenuPermissions(cachedPermissions.get());
            // CO-438: 合并 DB RoleProfile 的管理权限点
            // OSS menuCode→权限码映射表不含 performance.manage/warehouse.manage/personnel.manage
            // 这些写操作权限点只存在于 DB RoleProfile，OSS 用户若不合并将看不到管理按钮
            Optional<String> cachedRoleCode = ossPermissionCache.getRoleCode(user.getUsername());
            if (cachedRoleCode.isPresent()) {
                RoleProfileCatalog.SeedDefinition def = RoleProfileCatalog.definitionForCode(cachedRoleCode.get());
                if (def != null && def.menuPermissions() != null && !def.menuPermissions().isEmpty()) {
                    java.util.LinkedHashSet<String> merged = new java.util.LinkedHashSet<>(ossPermissions);
                    merged.addAll(def.menuPermissions());
                    return List.copyOf(merged);
                }
            }
            return ossPermissions;
        }
        // admin 系统内置账户不走 OSS，fallback 到本地 DB RoleProfile
        if (isLocalSystemAccount(user)) {
            List<String> localPermissions = resolveRoleProfile(user).getMenuPermissions();
            if (localPermissions != null && !localPermissions.isEmpty()) {
                log.info("Local system account user={} using DB RoleProfile menu_permissions", user.getUsername());
                return normalizeMenuPermissions(localPermissions);
            }
        }
        log.warn("OSS permission cache miss for user={}, returning empty (need re-login)", user.getUsername());
        return List.of();
    }

    /** admin 系统内置账户（不走 OSS 认证）：externalOrgSourceApp 为空且角色码为 admin。 */
    private boolean isLocalSystemAccount(User user) {
        return (user.getExternalOrgSourceApp() == null || user.getExternalOrgSourceApp().isBlank())
                && RoleProfileCatalog.ADMIN_CODE.equals(user.getRoleCode());
    }

    public String getRoleCode(User user) {
        if (user == null) return null;
        Optional<String> cachedRoleCode = ossPermissionCache.getRoleCode(user.getUsername());
        if (cachedRoleCode.isPresent()) return cachedRoleCode.get();
        // admin 系统内置账户（不走 OSS 认证）：cache miss 时 fallback 到本地 DB RoleProfile
        if (isLocalSystemAccount(user)) {
            String dbRoleCode = user.getRoleCode();
            if (dbRoleCode != null && !dbRoleCode.isBlank()) return dbRoleCode;
        }
        // OSS 用户 cache miss：fail-closed，不 fallback 到 DB roleCode
        // 原因：OSS 用户的 DB roleCode 可能是 /bidAdmin 等历史同步值，fallback 会导致越权拿到 DB 权限。
        // OSS 用户权限以 OSS 缓存为准，缓存失效应要求重新登录（与 getRoleMenuPermissions 一致）。
        log.warn("OSS role cache miss for user={}, returning null (need re-login)", user.getUsername());
        return null;
    }

    public String getRoleName(User user) {
        if (user == null) return "员工";
        Optional<String> cachedRoleCode = ossPermissionCache.getRoleCode(user.getUsername());
        if (cachedRoleCode.isPresent()) {
            String roleCode = cachedRoleCode.get();
            RoleProfileCatalog.SeedDefinition def = RoleProfileCatalog.definitionForCode(roleCode);
            if (def != null && def.name() != null && !def.name().isBlank()) return def.name();
            return roleCode;
        }
        // admin 系统内置账户（不走 OSS 认证）：cache miss 时 fallback 到本地 DB RoleProfile
        if (isLocalSystemAccount(user)) {
            RoleProfile roleProfile = resolveRoleProfile(user);
            if (roleProfile.getName() != null && !roleProfile.getName().isBlank()) return roleProfile.getName();
        }
        // OSS 用户 cache miss：fail-closed，不 fallback 到 DB roleName
        // 原因：与 getRoleCode 保持一致，避免 OSS 用户拿到 /bidAdmin 等 DB 权限对应的角色名。
        // 返回 null 而非 "员工"，避免前端误展示默认角色名而掩盖权限失效的真实状态。
        log.warn("OSS role cache miss for user={}, returning null (need re-login)", user.getUsername());
        return null;
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
        // DB 无记录时：已注册角色（含 roleCode=null 的纯 Legacy 用户）走 catalog fallback；
        // 未注册 roleCode 不 fallback 到 staff——避免前端 AuthResponse.menuPermissions 越权可见
        // 标讯/项目/知识库菜单。后端 API 已由 UserDetailsServiceImpl 的 shouldSkipLegacyRoleCompat
        // 挡住（403），此处收紧前端可见性，消除"看到菜单却点不进"的不一致。
        if (roleCode != null && !roleCode.isBlank() && !RoleProfileCatalog.isRegisteredCode(roleCode)) {
            return unregisteredPlaceholder(roleCode);
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

    private RoleProfile unregisteredPlaceholder(String roleCode) {
        RoleProfile placeholder = RoleProfile.builder()
                .code(roleCode)
                .name(roleCode)
                .isSystem(false)
                .enabled(true)
                .dataScope("self")
                .build();
        placeholder.setMenuPermissions(List.of());
        return placeholder;
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

}
