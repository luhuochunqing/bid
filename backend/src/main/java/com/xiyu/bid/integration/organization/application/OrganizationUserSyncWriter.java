package com.xiyu.bid.integration.organization.application;

import com.xiyu.bid.integration.organization.domain.policy.JobRoleLookupResolver;
import com.xiyu.bid.integration.organization.domain.policy.JobRoleLookupResolver.ResolvedRole;
import com.xiyu.bid.integration.organization.domain.policy.JobRoleLookupResolver.RoleMappingSource;
import com.xiyu.bid.integration.organization.domain.OrganizationSyncPolicy;
import com.xiyu.bid.integration.organization.domain.OrganizationUserSnapshot;
import com.xiyu.bid.integration.organization.domain.OrganizationUserSyncPlan;
import com.xiyu.bid.integration.organization.dto.OssUserJobAndRoleDto;
import com.xiyu.bid.security.domain.LoginRoleWhitelist;
import com.xiyu.bid.integration.organization.infrastructure.persistence.entity.OrganizationDepartmentEntity;
import com.xiyu.bid.integration.organization.infrastructure.persistence.repository.OrganizationDepartmentRepository;
import com.xiyu.bid.entity.RoleProfile;
import com.xiyu.bid.entity.RoleProfileCatalog;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.RoleProfileRepository;
import com.xiyu.bid.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrganizationUserSyncWriter {
    public static final String LOCKED_PASSWORD_HASH = "$2a$10$7EqJtq98hPqEX7fNZaFWoOHIhi4YhML26vP7Hk1UR93E1Vda8yI9W";

    /** 默认测试密码 123456 的 BCrypt 编码，用于 OSS 同步员工本地登录。 */
    public static final String DEFAULT_PASSWORD_HASH = "$2a$10$FwCOuxKv3WA8f2uwiUE23umE0ooMOPDOoOs2JTK49zN8i8PYLxK4y";

    private final UserRepository userRepository;
    private final RoleProfileRepository roleProfileRepository;
    private final OrganizationDepartmentRepository organizationDepartmentRepository;
    private final OrganizationIntegrationProperties properties;
    private final JobRoleLookupResolver jobRoleLookupResolver;
    private final OssRoleMenuPermissionAutoSync ossRoleMenuPermissionAutoSync;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @CacheEvict(value = "users:enabled", key = "'all'")
    public Optional<User> upsert(String sourceApp, String eventKey, OrganizationUserSnapshot snapshot) {
        return upsert(sourceApp, eventKey, snapshot, Map.of());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @CacheEvict(value = "users:enabled", key = "'all'")
    public Optional<User> upsert(
            String sourceApp,
            String eventKey,
            OrganizationUserSnapshot snapshot,
            Map<String, OssUserJobAndRoleDto> jobRoleLookupMap
    ) {
        validateRequiredContact(snapshot);
        Optional<User> existingUser = userRepository.findByExternalOrgSourceAppAndExternalOrgUserId(sourceApp, snapshot.externalUserId());
        User user = existingUser.orElseGet(User::new);

        OrganizationUserSnapshot enrichedSnapshot = enrichDepartmentName(sourceApp, snapshot);
        ResolvedRole resolvedRole = jobRoleLookupResolver.resolve(enrichedSnapshot, jobRoleLookupMap);
        String resolvedRoleCode = resolvedRole.roleCode();

        if (!LoginRoleWhitelist.isAllowed(resolvedRoleCode)) {
            handleUnmappedUser(sourceApp, eventKey, snapshot, existingUser);
            return Optional.empty();
        }

        boolean allowAdminElevation = resolvedRole.source() == RoleMappingSource.PERSON;
        OrganizationUserSyncPlan plan = OrganizationSyncPolicy.planUserSync(
                snapshot,
                // SAFE: OSS 同步场景特有 — 同步时新用户的 DB roleCode 是上一次同步的快照值，
                // 不存在"OSS 缓存未命中"的场景（同步本身就是要更新 DB）。这里读取的是"待同步 DB 当前值"
                // 用于对比决策，不是权限判定。CO-373 治理范围外。
                user.getRoleCode(),
                normalizeSet(properties.getAdminRoleCodes()),
                normalizeSet(properties.getManagerRoleCodes()),
                resolvedRoleCode,
                allowAdminElevation
        );
        user.setUsername(plan.username());
        // CO-441: OSS 同步用户的工号同时写入 employee_number，保持与 username 一致。
        // 历史上只写 username，导致 TenderAutoAssignmentService.resolveManagerNameByEmployeeNumber
        // 按 employee_number 查询时返回 null，CRM 自动分配失败。V1126 迁移脚本回填历史数据。
        user.setEmployeeNumber(plan.username());
        user.setPassword(user.getPassword() == null ? DEFAULT_PASSWORD_HASH : user.getPassword());
        user.setEmail(plan.email());
        user.setFullName(plan.fullName());
        user.setPhone(plan.phone());
        user.setDepartmentCode(plan.departmentCode());
        user.setDepartmentName(plan.departmentName());
        user.setEnabled(plan.enabled());
        user.setExternalOrgUserId(snapshot.externalUserId());
        user.setExternalOrgSourceApp(sourceApp);
        user.setLastOrgEventKey(eventKey);
        user.setLastOrgSyncedAt(LocalDateTime.now());
        applyRole(user, plan.roleCode());
        User saved = userRepository.save(user);
        if (properties.getDirectory().isAutoSyncMenuPermissions()) {
            autoSyncMenuPermissions(saved);
        }
        return Optional.of(saved);
    }

    private void autoSyncMenuPermissions(User user) {
        RoleProfile role = user.getRoleProfile();
        if (role == null || user.getUsername() == null || user.getUsername().isBlank()) {
            return;
        }
        try {
            ossRoleMenuPermissionAutoSync.mergeUserMenuPermissionsIntoRole(user.getUsername(), role);
        } catch (RuntimeException ex) {
            log.warn("自动同步用户 OSS 菜单权限失败: userId={}, roleCode={}, error={}",
                user.getId(), role.getCode(), ex.getMessage());
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @CacheEvict(value = "users:enabled", key = "'all'")
    public void disableByExternalId(String sourceApp, String eventKey, String externalUserId) {
        userRepository.findByExternalOrgSourceAppAndExternalOrgUserId(sourceApp, externalUserId)
                .ifPresent(user -> {
                    user.setEnabled(false);
                    user.setLastOrgEventKey(eventKey);
                    user.setLastOrgSyncedAt(LocalDateTime.now());
                    userRepository.save(user);
                });
    }

    private OrganizationUserSnapshot enrichDepartmentName(String sourceApp, OrganizationUserSnapshot snapshot) {
        String deptName = snapshot.departmentName();
        if ((deptName == null || deptName.isBlank())
                && snapshot.departmentCode() != null && !snapshot.departmentCode().isBlank()) {
            deptName = organizationDepartmentRepository
                    .findBySourceAppAndExternalDeptId(sourceApp, snapshot.departmentCode())
                    .map(OrganizationDepartmentEntity::getDepartmentName)
                    .orElse(deptName);
            if (deptName != null && !deptName.isBlank()) {
                return new OrganizationUserSnapshot(
                        snapshot.externalUserId(),
                        snapshot.username(),
                        snapshot.fullName(),
                        snapshot.email(),
                        snapshot.phone(),
                        snapshot.departmentCode(),
                        deptName,
                        snapshot.jobId(),
                        snapshot.externalRoleCode(),
                        snapshot.enabled()
                );
            }
        }
        return snapshot;
    }

    private void validateRequiredContact(OrganizationUserSnapshot snapshot) {
        if (snapshot.email() == null || snapshot.email().isBlank()) {
            throw new IllegalArgumentException("组织架构用户邮箱不能为空");
        }
        if (snapshot.phone() == null || snapshot.phone().isBlank()) {
            throw new IllegalArgumentException("组织架构用户手机号不能为空");
        }
    }

    private void applyRole(User user, String roleCode) {
        RoleProfile roleProfile = roleProfileRepository.findByCodeIgnoreCase(roleCode).orElse(null);
        user.setRoleProfile(roleProfile);
        user.setRole(RoleProfileCatalog.legacyRoleForCode(roleProfile == null ? roleCode : roleProfile.getCode()));
    }

    private Set<String> normalizeSet(java.util.List<String> values) {
        return values.stream().map(value -> value.trim().toLowerCase(java.util.Locale.ROOT)).collect(java.util.stream.Collectors.toSet());
    }

    /**
     * 白名单过滤：未命中任何映射的 OSS 用户，本地已存在则禁用，不存在则跳过。
     */
    private void handleUnmappedUser(String sourceApp, String eventKey, OrganizationUserSnapshot snapshot, Optional<User> existingUser) {
        existingUser.ifPresent(user -> {
            user.setEnabled(false);
            user.setLastOrgEventKey(eventKey);
            user.setLastOrgSyncedAt(LocalDateTime.now());
            userRepository.save(user);
        });
    }
}
