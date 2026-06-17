package com.xiyu.bid.integration.organization.application;

import com.xiyu.bid.integration.organization.infrastructure.persistence.entity.OrganizationDepartmentEntity;
import com.xiyu.bid.integration.organization.infrastructure.persistence.repository.OrganizationDepartmentRepository;
import com.xiyu.bid.entity.RoleProfile;
import com.xiyu.bid.entity.RoleProfileCatalog;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.integration.organization.domain.OrganizationSyncPolicy;
import com.xiyu.bid.integration.organization.domain.OrganizationUserSnapshot;
import com.xiyu.bid.integration.organization.domain.OrganizationUserSyncPlan;
import com.xiyu.bid.integration.organization.infrastructure.mapper.PositionToRoleMapper;
import com.xiyu.bid.integration.organization.domain.OrganizationJobSnapshot;
import com.xiyu.bid.integration.organization.application.OrganizationDirectoryGateway;
import org.springframework.beans.factory.ObjectProvider;
import com.xiyu.bid.repository.RoleProfileRepository;
import com.xiyu.bid.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class OrganizationUserSyncWriter {
    private static final String LOCKED_PASSWORD_HASH = "$2a$10$7EqJtq98hPqEX7fNZaFWoOHIhi4YhML26vP7Hk1UR93E1Vda8yI9W";

    /** OSS 角色码到内部角色码的映射 */
    private static final java.util.Map<String, String> OSS_TO_INTERNAL_ROLE = java.util.Map.of(
        "/bidAdmin", "bid_admin",
        "bid-TeamLeader", "bid_lead",
        "bid-SystemAdmin", "bid_admin",
        "bid-Team", "bid_specialist",
        "bid-projectLeader", "sales",
        "bid-administration", "admin_staff",
        "bid-otherDept", "bid_other_dept"
    );

    private final UserRepository userRepository;
    private final OrganizationDepartmentRepository organizationDepartmentRepository;
    private final RoleProfileRepository roleProfileRepository;
    private final OrganizationIntegrationProperties properties;
    private final PositionToRoleMapper positionToRoleMapper;
    private final ObjectProvider<OrganizationDirectoryGateway> directoryGatewayProvider;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public User upsert(String sourceApp, String eventKey, OrganizationUserSnapshot snapshot) {
        validateRequiredContact(snapshot);
        Optional<User> existingUser = userRepository.findByExternalOrgSourceAppAndExternalOrgUserId(sourceApp, snapshot.externalUserId());
        User user = existingUser.orElseGet(User::new);
        // 按优先级解析角色码：按人员 > 按部门 > 按岗位
        String personMappedRoleCode = mapPersonToRole(snapshot);
        String deptMappedRoleCode = mapDepartmentToRole(snapshot);
        String positionMappedRoleCode = positionToRoleMapper.map(snapshot.externalRoleCode());
        // 如果 externalRoleCode 为空但 jobId 存在，回查岗位数据用于岗位→角色映射
        if ((positionMappedRoleCode == null || positionMappedRoleCode.isBlank())
            && snapshot.jobId() != null && !snapshot.jobId().isBlank()) {
            OrganizationDirectoryGateway gateway = directoryGatewayProvider.getIfAvailable();
            if (gateway != null) {
                Optional<OrganizationJobSnapshot> jobOpt = gateway.fetchJobByJobId(snapshot.jobId());
                if (jobOpt.isPresent()) {
                    OrganizationJobSnapshot job = jobOpt.get();
                    positionMappedRoleCode = positionToRoleMapper.map(job.jobCode());
                    if (positionMappedRoleCode == null || positionMappedRoleCode.isBlank()) {
                        positionMappedRoleCode = positionToRoleMapper.map(job.jobName());
                    }
                }
            }
        }
        String resolvedRoleCode = firstNonNull(personMappedRoleCode, deptMappedRoleCode, positionMappedRoleCode);
        // 将 OSS 角色码映射为内部角色码
        if (resolvedRoleCode != null) {
            resolvedRoleCode = OSS_TO_INTERNAL_ROLE.getOrDefault(resolvedRoleCode, resolvedRoleCode);
        }
        if (properties.isSkipUnmappedUsers() && (resolvedRoleCode == null || resolvedRoleCode.isBlank())) {
            handleUnmappedUser(sourceApp, eventKey, snapshot, existingUser);
            return null;
        }
        // 按人员映射时允许 admin 升级；按部门/岗位映射仍受 Admin 升级守卫保护。
        boolean allowAdminElevation = personMappedRoleCode != null && !personMappedRoleCode.isBlank();
        OrganizationUserSyncPlan plan = OrganizationSyncPolicy.planUserSync(
                snapshot,
                user.getRoleCode(),
                normalizeSet(properties.getAdminRoleCodes()),
                normalizeSet(properties.getManagerRoleCodes()),
                resolvedRoleCode,
                allowAdminElevation
        );
        user.setUsername(plan.username());
        user.setPassword(user.getPassword() == null ? LOCKED_PASSWORD_HASH : user.getPassword());
        user.setEmail(plan.email());
        user.setFullName(plan.fullName());
        user.setPhone(plan.phone());
        user.setDepartmentCode(plan.departmentCode());
        user.setDepartmentName(plan.departmentName());
        // 如果部门名称为空但 departmentCode 有值，从 organization_departments 反查部门名称
        String resolvedDeptName = plan.departmentName();
        if ((resolvedDeptName == null || resolvedDeptName.isBlank())
            && plan.departmentCode() != null && !plan.departmentCode().isBlank()) {
            resolvedDeptName = organizationDepartmentRepository
                .findBySourceAppAndExternalDeptId(sourceApp, plan.departmentCode())
                .map(OrganizationDepartmentEntity::getDepartmentName)
                .orElse("");
        }
        user.setDepartmentName(resolvedDeptName);
        user.setEnabled(plan.enabled());
        user.setExternalOrgUserId(snapshot.externalUserId());
        user.setExternalOrgSourceApp(sourceApp);
        user.setLastOrgEventKey(eventKey);
        user.setLastOrgSyncedAt(LocalDateTime.now());
        applyRole(user, plan.roleCode());
        return userRepository.save(user);
    }

    private void validateRequiredContact(OrganizationUserSnapshot snapshot) {
        if (snapshot.email() == null || snapshot.email().isBlank()) {
            throw new IllegalArgumentException("组织架构用户邮箱不能为空");
        }
        if (snapshot.phone() == null || snapshot.phone().isBlank()) {
            throw new IllegalArgumentException("组织架构用户手机号不能为空");
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void disableByExternalId(String sourceApp, String eventKey, String externalUserId) {
        userRepository.findByExternalOrgSourceAppAndExternalOrgUserId(sourceApp, externalUserId)
                .ifPresent(user -> {
                    user.setEnabled(false);
                    user.setLastOrgEventKey(eventKey);
                    user.setLastOrgSyncedAt(LocalDateTime.now());
                    userRepository.save(user);
                });
    }

    private void applyRole(User user, String roleCode) {
        RoleProfile roleProfile = roleProfileRepository.findByCodeIgnoreCase(roleCode)
                .or(() -> roleProfileRepository.findByCodeIgnoreCase(RoleProfileCatalog.STAFF_CODE))
                .orElse(null);
        user.setRoleProfile(roleProfile);
        user.setRole(RoleProfileCatalog.legacyRoleForCode(roleProfile == null ? roleCode : roleProfile.getCode()));
    }

    private Set<String> normalizeSet(java.util.List<String> values) {
        return values.stream().map(value -> value.trim().toLowerCase(Locale.ROOT)).collect(java.util.stream.Collectors.toSet());
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

    /**
     * 按人员映射：通过西域员工邮箱/工号/用户名/姓名精确匹配到指定角色
     * 优先级最高，用于指定人员（如管理员）直接赋予对应角色
     */
    private String mapPersonToRole(OrganizationUserSnapshot snapshot) {
        String email = snapshot.email();
        String userExternalId = snapshot.externalUserId();
        String snapshotUsername = snapshot.username();
        String fullName = snapshot.fullName();
        for (OrganizationIntegrationProperties.PersonToRoleMapping mapping : properties.getPersonToRoleMappings()) {
            if ((email != null && mapping.matches(email))
                    || (userExternalId != null && mapping.matches(userExternalId))
                    || (snapshotUsername != null && mapping.matches(snapshotUsername))
                    || (fullName != null && mapping.matches(fullName))) {
                return mapping.getRoleCode();
            }
        }
        return null;
    }

    /**
     * 按部门映射：通过西域部门名称正则匹配，部门全员获得同一角色
     * 优先级次于按人员映射，用于部门级别统一赋权（如投标管理部→投标专员）
     */
    private String mapDepartmentToRole(OrganizationUserSnapshot snapshot) {
        // 先从 snapshot 取，空则从 organization_departments 反查
        String deptName = snapshot.departmentName();
        if ((deptName == null || deptName.isBlank())
            && snapshot.departmentCode() != null && !snapshot.departmentCode().isBlank()) {
            deptName = organizationDepartmentRepository
                .findBySourceAppAndExternalDeptId("oss", snapshot.departmentCode())
                .map(OrganizationDepartmentEntity::getDepartmentName)
                .orElse(null);
        }
        if (deptName == null || deptName.isBlank()) {
            return null;
        }
        for (OrganizationIntegrationProperties.DepartmentToRoleMapping mapping : properties.getDepartmentToRoleMappings()) {
            if (mapping.matches(deptName)) {
                return mapping.getRoleCode();
            }
        }
        return null;
    }

    /**
     * 取第一个非空非空白值，用于多级角色映射优先级决议
     */
    private static String firstNonNull(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v.trim().toLowerCase(Locale.ROOT);
            }
        }
        return null;
    }

}
