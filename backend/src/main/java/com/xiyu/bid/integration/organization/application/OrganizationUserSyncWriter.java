package com.xiyu.bid.integration.organization.application;

import com.xiyu.bid.entity.RoleProfile;
import com.xiyu.bid.entity.RoleProfileCatalog;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.integration.organization.domain.OrganizationSyncPolicy;
import com.xiyu.bid.integration.organization.domain.OrganizationUserSnapshot;
import com.xiyu.bid.integration.organization.domain.OrganizationUserSyncPlan;
import com.xiyu.bid.integration.organization.infrastructure.mapper.PositionToRoleMapper;
import com.xiyu.bid.repository.RoleProfileRepository;
import com.xiyu.bid.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class OrganizationUserSyncWriter {
    private static final String LOCKED_PASSWORD_HASH = "$2a$10$7EqJtq98hPqEX7fNZaFWoOHIhi4YhML26vP7Hk1UR93E1Vda8yI9W";

    private final UserRepository userRepository;
    private final RoleProfileRepository roleProfileRepository;
    private final OrganizationIntegrationProperties properties;
    private final PositionToRoleMapper positionToRoleMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public User upsert(String sourceApp, String eventKey, OrganizationUserSnapshot snapshot) {
        validateRequiredContact(snapshot);
        User user = userRepository.findByExternalOrgSourceAppAndExternalOrgUserId(sourceApp, snapshot.externalUserId())
                .orElseGet(User::new);
        String positionMappedRoleCode = positionToRoleMapper.map(snapshot.externalRoleCode());
        OrganizationUserSyncPlan plan = OrganizationSyncPolicy.planUserSync(
                snapshot,
                user.getRoleCode(),
                normalizeSet(properties.getAdminRoleCodes()),
                normalizeSet(properties.getManagerRoleCodes()),
                positionMappedRoleCode
        );
        user.setUsername(plan.username());
        user.setPassword(user.getPassword() == null ? LOCKED_PASSWORD_HASH : user.getPassword());
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

}
