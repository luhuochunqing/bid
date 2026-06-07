package com.xiyu.bid.service;
import com.xiyu.bid.entity.RoleProfileCatalog;

import com.xiyu.bid.admin.service.DataScopeConfigService;
import com.xiyu.bid.admin.settings.core.DepartmentGraphPolicy;
import com.xiyu.bid.admin.settings.core.OrganizationValidationPolicy;
import com.xiyu.bid.admin.settings.core.OrganizationValidationResult;
import com.xiyu.bid.dto.AdminUserCreateRequest;
import com.xiyu.bid.dto.AdminUserDTO;
import com.xiyu.bid.dto.AdminUserStatusUpdateRequest;
import com.xiyu.bid.dto.AdminUserUpdateRequest;
import com.xiyu.bid.dto.UserOrganizationUpdateRequest;
import com.xiyu.bid.entity.RoleProfile;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.util.InputSanitizer;
import com.xiyu.bid.util.PasswordValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminUserService {

    private static final long MIN_ENABLED_ADMINS = 1L;
    private static final Set<Long> DISABLED_ROLE_SENTINEL = Set.of(-1L);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RoleProfileService roleProfileService;
    private final DataScopeConfigService dataScopeConfigService;

    public List<AdminUserDTO> listUsers() {
        return userRepository.findAll().stream()
                .sorted((left, right) -> String.CASE_INSENSITIVE_ORDER.compare(left.getUsername(), right.getUsername()))
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public AdminUserDTO createUser(AdminUserCreateRequest request) {
        String username = sanitize(request.getUsername(), 50);
        String email = sanitize(request.getEmail(), 100);
        String fullName = sanitize(request.getFullName(), 100);
        String phone = sanitizePhone(request.getPhone());
        String departmentCode = sanitize(request.getDepartmentCode(), 100);
        String departmentName = sanitize(request.getDepartmentName(), 100);

        validateNewUser(username, email, request.getPassword(), phone);

        User user = User.builder()
                .username(username)
                .password(passwordEncoder.encode(request.getPassword()))
                .email(email)
                .fullName(fullName)
                .phone(phone)
                .departmentCode(departmentCode)
                .departmentName(departmentName)
                .enabled(Boolean.TRUE.equals(request.getEnabled()))
                .build();
        applyRole(user, roleProfileService.requireRoleProfile(request.getRoleId()));

        User savedUser = userRepository.save(user);
        log.info("Admin created user: {}", savedUser.getUsername());
        return toDto(savedUser);
    }

    @Transactional
    public AdminUserDTO updateUser(Long userId, AdminUserUpdateRequest request, String operatorUsername) {
        User user = findUser(userId);

        String username = sanitize(request.getUsername(), 50);
        String email = sanitize(request.getEmail(), 100);
        String fullName = sanitize(request.getFullName(), 100);
        String phone = sanitizePhone(request.getPhone());
        String departmentCode = sanitize(request.getDepartmentCode(), 100);
        String departmentName = sanitize(request.getDepartmentName(), 100);
        boolean enabled = Boolean.TRUE.equals(request.getEnabled());
        RoleProfile nextRoleProfile = roleProfileService.requireRoleProfile(request.getRoleId());

        validateExistingUser(userId, username, email, phone);
        ensureActiveAdminRetained(user, nextRoleProfile, enabled, operatorUsername);

        user.setUsername(username);
        user.setEmail(email);
        user.setFullName(fullName);
        user.setPhone(phone);
        user.setDepartmentCode(departmentCode);
        user.setDepartmentName(departmentName);
        user.setEnabled(enabled);
        applyRole(user, nextRoleProfile);

        User savedUser = userRepository.save(user);
        log.info("Admin updated user: {}", savedUser.getUsername());
        return toDto(savedUser);
    }

    @Transactional
    public AdminUserDTO updateStatus(Long userId, AdminUserStatusUpdateRequest request, String operatorUsername) {
        User user = findUser(userId);
        boolean enabled = Boolean.TRUE.equals(request.getEnabled());

        ensureActiveAdminRetained(user, user.getRoleProfile(), enabled, operatorUsername);

        user.setEnabled(enabled);
        User savedUser = userRepository.save(user);
        log.info("Admin updated user status: {} -> {}", savedUser.getUsername(), enabled);
        return toDto(savedUser);
    }

    @Transactional
    public AdminUserDTO updateOrganization(Long userId, UserOrganizationUpdateRequest request, String operatorUsername) {
        User user = findUser(userId);
        RoleProfile nextRoleProfile = roleProfileService.requireRoleProfile(request.getRoleId());
        boolean enabled = Boolean.TRUE.equals(request.getEnabled());
        Map<String, String> deptNameByCode = dataScopeConfigService.getDepartmentGraph().options().stream()
                .collect(Collectors.toMap(option -> option.code(), option -> option.name()));
        String departmentCode = DepartmentGraphPolicy.normalizeCode(request.getDepartmentCode());

        OrganizationValidationResult validation = OrganizationValidationPolicy.validateUserOrganization(
                enabled,
                departmentCode,
                request.getRoleId(),
                deptNameByCode.keySet(),
                Boolean.TRUE.equals(nextRoleProfile.getEnabled()) ? Set.of(nextRoleProfile.getId()) : DISABLED_ROLE_SENTINEL
        );
        if (!validation.valid()) {
            throw new IllegalArgumentException(validation.message());
        }

        ensureActiveAdminRetained(user, nextRoleProfile, enabled, operatorUsername);
        user.setDepartmentCode(departmentCode);
        user.setDepartmentName(deptNameByCode.get(departmentCode));
        user.setEnabled(enabled);
        applyRole(user, nextRoleProfile);
        return toDto(userRepository.save(user));
    }

    @Transactional
    public void deleteUser(Long userId, String operatorUsername) {
        User user = findUser(userId);

        if (user.getUsername().equalsIgnoreCase(operatorUsername)) {
            throw new IllegalStateException("You cannot delete your own account");
        }

        if (roleProfileService.isAdminRole(user) && Boolean.TRUE.equals(user.getEnabled())) {
            long enabledAdminCount = userRepository.countByRoleProfile_CodeIgnoreCaseAndEnabledTrue(RoleProfileCatalog.ADMIN_CODE);
            if (enabledAdminCount <= MIN_ENABLED_ADMINS) {
                throw new IllegalStateException("At least one enabled admin must remain");
            }
        }

        userRepository.delete(user);
        log.info("Admin deleted user: {}", user.getUsername());
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
    }

    private void validateNewUser(String username, String email, String password, String phone) {
        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("Username already exists");
        }
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email already exists");
        }
        if (!phone.isBlank() && !InputSanitizer.isValidPhone(phone)) {
            throw new IllegalArgumentException("Phone format is invalid");
        }
        PasswordValidator.ValidationResult passwordValidation = PasswordValidator.validate(password);
        if (!passwordValidation.isValid()) {
            throw new IllegalArgumentException(passwordValidation.getMessage());
        }
    }

    private void validateExistingUser(Long userId, String username, String email, String phone) {
        if (userRepository.existsByUsernameAndIdNot(username, userId)) {
            throw new IllegalArgumentException("Username already exists");
        }
        if (userRepository.existsByEmailAndIdNot(email, userId)) {
            throw new IllegalArgumentException("Email already exists");
        }
        if (!phone.isBlank() && !InputSanitizer.isValidPhone(phone)) {
            throw new IllegalArgumentException("Phone format is invalid");
        }
    }

    private void ensureActiveAdminRetained(User user, RoleProfile nextRoleProfile, boolean nextEnabled, String operatorUsername) {
        boolean roleStaysAdmin = nextRoleProfile != null
                && RoleProfileCatalog.ADMIN_CODE.equalsIgnoreCase(nextRoleProfile.getCode());
        boolean staysEnabled = nextEnabled;

        if (roleProfileService.isAdminRole(user) && (!roleStaysAdmin || !staysEnabled)) {
            long enabledAdminCount = userRepository.countByRoleProfile_CodeIgnoreCaseAndEnabledTrue(RoleProfileCatalog.ADMIN_CODE);
            if (enabledAdminCount <= MIN_ENABLED_ADMINS) {
                throw new IllegalStateException("At least one enabled admin must remain");
            }
        }

        if (user.getUsername().equalsIgnoreCase(operatorUsername) && !nextEnabled) {
            throw new IllegalStateException("You cannot disable the current admin account");
        }

        if (user.getUsername().equalsIgnoreCase(operatorUsername) && !roleStaysAdmin) {
            throw new IllegalStateException("You cannot change the current admin account to a non-admin role");
        }
    }

    private void applyRole(User user, RoleProfile roleProfile) {
        user.setRoleProfile(roleProfile);
        user.setRole(RoleProfileCatalog.legacyRoleForCode(roleProfile == null ? null : roleProfile.getCode()));
    }

    private String sanitize(String value, int maxLength) {
        return InputSanitizer.sanitizeString(value, maxLength);
    }

    private String sanitizePhone(String value) {
        return sanitize(value, 32);
    }

    private AdminUserDTO toDto(User user) {
        return AdminUserDTO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .departmentCode(user.getDepartmentCode())
                .departmentName(user.getDepartmentName())
                .roleId(user.getRoleProfile() == null ? null : user.getRoleProfile().getId())
                .roleCode(user.getRoleCode())
                .roleName(user.getRoleName())
                .enabled(Boolean.TRUE.equals(user.getEnabled()))
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
