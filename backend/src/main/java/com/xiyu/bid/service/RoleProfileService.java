package com.xiyu.bid.service;
import com.xiyu.bid.entity.RoleProfileCatalog;

import com.xiyu.bid.admin.settings.core.OrganizationValidationPolicy;
import com.xiyu.bid.admin.settings.core.OrganizationValidationResult;
import com.xiyu.bid.dto.CreateRoleRequest;
import com.xiyu.bid.dto.RoleDTO;
import com.xiyu.bid.dto.UpdateRoleRequest;
import com.xiyu.bid.dto.UpdateRoleStatusRequest;
import com.xiyu.bid.entity.RoleProfile;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.RoleProfileRepository;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.roleprofile.RoleProfileBootstrap;
import com.xiyu.bid.util.InputSanitizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RoleProfileService {

    private static final List<String> ALLOWED_SCOPES = List.of("all", "dept", "deptAndSub", "self");

    private final RoleProfileRepository roleProfileRepository;
    private final UserRepository userRepository;
    private final RoleProfileBootstrap roleProfileBootstrap;

    public List<RoleDTO> listRoles() {
        ensureSystemRoles();
        List<RoleProfile> roles = roleProfileRepository.findAll();
        Map<Long, Integer> userCountByRoleId = new LinkedHashMap<>();
        for (RoleProfile role : roles) {
            userCountByRoleId.put(role.getId(), (int) userRepository.countByRoleProfile_Id(role.getId()));
        }
        return roles.stream()
                .sorted(Comparator.comparing(RoleProfile::getIsSystem).reversed()
                        .thenComparing(RoleProfile::getCode, String.CASE_INSENSITIVE_ORDER))
                .map(role -> toDto(role, userCountByRoleId.getOrDefault(role.getId(), 0)))
                .toList();
    }

    @Transactional
    public RoleDTO createRole(CreateRoleRequest request) {
        ensureSystemRoles();
        String code = sanitizeCode(request.getCode());
        if (roleProfileRepository.existsByCodeIgnoreCase(code)) {
            throw new IllegalArgumentException("Role code already exists");
        }

        RoleProfile role = RoleProfile.builder()
                .code(code)
                .name(sanitize(request.getName(), 100))
                .description(sanitize(request.getDescription(), 255))
                .isSystem(false)
                .enabled(Boolean.TRUE.equals(request.getEnabled()))
                .dataScope(normalizeScope(request.getDataScope()))
                .build();
        role.setMenuPermissions(request.getMenuPermissions());
        role.setAllowedProjects(request.getAllowedProjects());
        role.setAllowedDepts(normalizeDeptCodes(request.getAllowedDepts()));
        return toDto(roleProfileRepository.save(role), 0);
    }

    @Transactional
    public RoleDTO updateRole(Long roleId, UpdateRoleRequest request) {
        ensureSystemRoles();
        RoleProfile role = findRole(roleId);
        role.setName(sanitize(request.getName(), 100));
        role.setDescription(sanitize(request.getDescription(), 255));
        role.setDataScope(normalizeScope(request.getDataScope()));
        role.setEnabled(Boolean.TRUE.equals(request.getEnabled()));
        role.setMenuPermissions(request.getMenuPermissions());
        role.setAllowedProjects(request.getAllowedProjects());
        role.setAllowedDepts(normalizeDeptCodes(request.getAllowedDepts()));
        return toDto(roleProfileRepository.save(role), countUsers(role));
    }

    @Transactional
    public RoleDTO updateRoleStatus(Long roleId, UpdateRoleStatusRequest request) {
        ensureSystemRoles();
        RoleProfile role = findRole(roleId);
        boolean enabled = Boolean.TRUE.equals(request.getEnabled());
        if (!enabled && Boolean.TRUE.equals(role.getIsSystem()) && RoleProfileCatalog.ADMIN_CODE.equalsIgnoreCase(role.getCode())) {
            throw new IllegalStateException("System admin role cannot be disabled");
        }
        int userCount = countUsers(role);
        OrganizationValidationResult validation = OrganizationValidationPolicy.validateRoleDeactivation(enabled, userCount);
        if (!validation.valid()) {
            throw new IllegalStateException(validation.message());
        }
        role.setEnabled(enabled);
        return toDto(roleProfileRepository.save(role), userCount);
    }

    @Transactional
    public RoleDTO resetRole(Long roleId) {
        ensureSystemRoles();
        RoleProfile role = findRole(roleId);
        if (!Boolean.TRUE.equals(role.getIsSystem())) {
            throw new IllegalStateException("Only system roles can be reset");
        }
        RoleProfileCatalog.SeedDefinition definition = RoleProfileCatalog.definitionForCode(role.getCode());
        applySeed(role, definition);
        return toDto(roleProfileRepository.save(role), countUsers(role));
    }

    @Transactional
    public void deleteRole(Long roleId) {
        ensureSystemRoles();
        RoleProfile role = findRole(roleId);

        if (Boolean.TRUE.equals(role.getIsSystem())) {
            throw new IllegalStateException("Built-in system roles cannot be deleted");
        }

        if (countUsers(role) > 0) {
            throw new IllegalStateException("Role is assigned to users and cannot be deleted");
        }

        roleProfileRepository.delete(role);
        log.info("Admin deleted role: {}", role.getCode());
    }

    @Transactional
    public void ensureSystemRoles() {
        roleProfileBootstrap.ensureSystemRoles();
    }

    public RoleProfile requireRoleProfile(Long roleId) {
        ensureSystemRoles();
        return findRole(roleId);
    }

    public RoleProfile resolveRoleProfile(String roleCode, User.Role legacyRole) {
        ensureSystemRoles();
        String normalizedCode = sanitizeCode(roleCode);
        if (!normalizedCode.isBlank()) {
            return roleProfileRepository.findByCodeIgnoreCase(normalizedCode)
                    .orElseThrow(() -> new IllegalArgumentException("Role not found"));
        }
        return roleProfileRepository.findByCodeIgnoreCase(RoleProfileCatalog.definitionForLegacyRole(legacyRole).code())
                .orElseThrow(() -> new IllegalStateException("Default role profile not found"));
    }

    public boolean isAdminRole(User user) {
        return user != null && RoleProfileCatalog.ADMIN_CODE.equalsIgnoreCase(user.getRoleCode());
    }

    private RoleProfile findRole(Long roleId) {
        return roleProfileRepository.findById(roleId)
                .orElseThrow(() -> new IllegalArgumentException("Role not found"));
    }

    private int countUsers(RoleProfile role) {
        return (int) userRepository.countByRoleProfile_Id(role.getId());
    }

    private RoleDTO toDto(RoleProfile role, int userCount) {
        return RoleDTO.builder()
                .id(role.getId())
                .code(role.getCode())
                .name(role.getName())
                .description(role.getDescription())
                .system(Boolean.TRUE.equals(role.getIsSystem()))
                .enabled(Boolean.TRUE.equals(role.getEnabled()))
                .userCount(userCount)
                .dataScope(role.getDataScope())
                .menuPermissions(role.getMenuPermissions())
                .allowedProjects(role.getAllowedProjects())
                .allowedDepts(role.getAllowedDepts())
                .createdAt(role.getCreatedAt())
                .updatedAt(role.getUpdatedAt())
                .build();
    }

    private void applySeed(RoleProfile role, RoleProfileCatalog.SeedDefinition definition) {
        role.setName(definition.name());
        role.setDescription(definition.description());
        role.setEnabled(true);
        role.setIsSystem(definition.system());
        role.setDataScope(definition.dataScope());
        role.setMenuPermissions(definition.menuPermissions());
        role.setAllowedProjects(List.of());
        role.setAllowedDepts(List.of());
    }

    private String sanitize(String value, int maxLength) {
        return InputSanitizer.sanitizeString(value, maxLength);
    }

    private String sanitizeCode(String value) {
        return sanitize(value, 64).replaceAll("[^a-zA-Z0-9_-]", "").toLowerCase(java.util.Locale.ROOT);
    }

    private String normalizeScope(String value) {
        String normalized = sanitize(value, 32);
        if (!ALLOWED_SCOPES.contains(normalized)) {
            return "self";
        }
        return normalized;
    }

    private List<String> normalizeDeptCodes(List<String> values) {
        return values == null ? List.of() : values.stream()
                .map(value -> sanitize(value, 100))
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
    }
}
