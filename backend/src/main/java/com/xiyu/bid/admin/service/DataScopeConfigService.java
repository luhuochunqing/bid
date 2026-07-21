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
import com.xiyu.bid.permission.RoleProfileAdminPermissionFilter;
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
    private final DataScopeRoleProfileResolver roleProfileResolver;

    // Manual constructor: encapsulates Store/Assembler/RoleProfileResolver as implementation details
    // so Spring only sees the thin application-service surface.
    @Autowired
    public DataScopeConfigService(
            SystemSettingRepository pSystemSettingRepository,
            UserRepository pUserRepository,
            RoleProfileRepository pRoleProfileRepository,
            RoleProfileBootstrap pRoleProfileBootstrap,
            ObjectMapper objectMapper,
            OssPermissionCache pOssPermissionCache,
            com.xiyu.bid.security.EffectiveRoleResolver pEffectiveRoleResolver
    ) {
        this(new DataScopeConfigStore(pSystemSettingRepository, objectMapper),
                new DataScopeConfigAssembler(),
                pUserRepository,
                pRoleProfileRepository,
                pRoleProfileBootstrap,
                pOssPermissionCache,
                new DataScopeRoleProfileResolver(pRoleProfileRepository, pEffectiveRoleResolver));
    }

    DataScopeConfigService(
            DataScopeConfigStore pConfigStore,
            DataScopeConfigAssembler pAssembler,
            UserRepository pUserRepository,
            RoleProfileRepository pRoleProfileRepository,
            RoleProfileBootstrap pRoleProfileBootstrap,
            OssPermissionCache pOssPermissionCache,
            DataScopeRoleProfileResolver pRoleProfileResolver
    ) {
        this.configStore = pConfigStore;
        this.assembler = pAssembler;
        this.userRepository = pUserRepository;
        this.roleProfileRepository = pRoleProfileRepository;
        this.roleProfileBootstrap = pRoleProfileBootstrap;
        this.ossPermissionCache = pOssPermissionCache;
        this.roleProfileResolver = pRoleProfileResolver;
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
                toRoleAccessRule(roleProfileResolver.resolve(user)),
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
        ResolvedRoleSource source = resolveRoleSource(user);
        if (source.cachedMenuPermissions().isPresent()) {
            List<String> ossPermissions = RoleProfileAdminPermissionFilter.normalize(source.cachedMenuPermissions().get());
            // specs/032: "all" 是内部 admin 专属权限键，OSS 用户不应持有（防御性兜底，OSS 实际返回菜单 codes 如 1001/1002）
            return RoleProfileAdminPermissionFilter.filter(ossPermissions);
        }
        // admin 系统内置账户不走 OSS，fallback 到本地 DB RoleProfile
        if (source.localSystemAccount()) {
            List<String> localPermissions = roleProfileResolver.resolve(user).getMenuPermissions();
            if (localPermissions != null && !localPermissions.isEmpty()) {
                log.info("Local system account user={} using DB RoleProfile menu_permissions", user.getUsername());
                return RoleProfileAdminPermissionFilter.normalize(localPermissions);
            }
        }
        log.warn("OSS permission cache miss for user={}, returning empty (need re-login)", user.getUsername());
        return List.of();
    }

    public String getRoleCode(User user) {
        if (user == null) return null;
        ResolvedRoleSource source = resolveRoleSource(user);
        if (source.cachedRoleCode().isPresent()) return source.cachedRoleCode().get();
        // admin 系统内置账户（不走 OSS 认证）：cache miss 时 fallback 到本地 DB RoleProfile
        if (source.localSystemAccount()) {
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
        ResolvedRoleSource source = resolveRoleSource(user);
        if (source.cachedRoleCode().isPresent()) {
            String roleCode = source.cachedRoleCode().get();
            RoleProfileCatalog.SeedDefinition def = RoleProfileCatalog.definitionForCode(roleCode);
            if (def != null && def.name() != null && !def.name().isBlank()) return def.name();
            return roleCode;
        }
        // admin 系统内置账户（不走 OSS 认证）：cache miss 时 fallback 到本地 DB RoleProfile
        if (source.localSystemAccount()) {
            RoleProfile roleProfile = roleProfileResolver.resolve(user);
            if (roleProfile.getName() != null && !roleProfile.getName().isBlank()) return roleProfile.getName();
        }
        // OSS 用户 cache miss：fail-closed，不 fallback 到 DB roleName
        // 原因：与 getRoleCode 保持一致，避免 OSS 用户拿到 /bidAdmin 等 DB 权限对应的角色名。
        // 返回 null 而非 "员工"，避免前端误展示默认角色名而掩盖权限失效的真实状态。
        log.warn("OSS role cache miss for user={}, returning null (need re-login)", user.getUsername());
        return null;
    }

    /** admin 系统内置账户（不走 OSS 认证）：externalOrgSourceApp 为空且角色码为 admin。 */
    private boolean isLocalSystemAccount(User user) {
        // SAFE: 仅用于区分本地 admin 系统账号与 OSS 同步用户；已用 user.isOssUser() 做第一道隔离，
        // 再走 user.getRoleCode() 读取本地 DB roleProfile 的 admin 判定，不会触发 CO-373 OSS fallback。
        return !user.isOssUser() && RoleProfileCatalog.ADMIN_CODE.equals(user.getRoleCode());
    }

    /**
     * 解析用户的角色/权限来源：优先 OSS 缓存，本地 admin 系统账户允许 DB 兜底，
     * OSS 用户 cache miss 时由调用方 fail-closed。
     * <p>统一封装 getRoleCode / getRoleName / getRoleMenuPermissions 重复的缓存+兜底判断。
     */
    private ResolvedRoleSource resolveRoleSource(User user) {
        Optional<OssPermissionCache.CacheEntry> cachedEntry = ossPermissionCache.getEntry(user.getUsername());
        Optional<String> cachedRoleCode = cachedEntry
                .filter(e -> e.roleCode() != null && !e.roleCode().isBlank())
                .map(OssPermissionCache.CacheEntry::roleCode);
        Optional<List<String>> cachedMenuPermissions = cachedEntry
                .filter(e -> e.menuPermissions() != null)
                .map(OssPermissionCache.CacheEntry::menuPermissions);
        return new ResolvedRoleSource(cachedRoleCode, cachedMenuPermissions, isLocalSystemAccount(user));
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

    private void validate(OrganizationValidationResult result) {
        if (!result.valid()) {
            throw new IllegalArgumentException(result.message());
        }
    }

}
