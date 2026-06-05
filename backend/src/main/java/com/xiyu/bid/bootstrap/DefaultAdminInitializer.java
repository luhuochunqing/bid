package com.xiyu.bid.bootstrap;

import com.xiyu.bid.entity.RoleProfile;
import com.xiyu.bid.entity.RoleProfileCatalog;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.RoleProfileRepository;
import com.xiyu.bid.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@Profile({"dev", "prod"})
@RequiredArgsConstructor
@Slf4j
public class DefaultAdminInitializer implements ApplicationRunner {

    static final int MIN_ADMIN_PASSWORD_LENGTH = 12;

    @Value("${app.bootstrap.admin.username:admin}")
    private String adminUsername;

    @Value("${app.bootstrap.admin.password}")
    private String adminPassword;

    @Value("${app.bootstrap.admin.email:admin@xiyu-local}")
    private String adminEmail;

    @Value("${app.bootstrap.admin.full-name:系统管理员}")
    private String adminFullName;

    private final UserRepository userRepository;
    private final RoleProfileRepository roleProfileRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Validate the admin password at startup, ALWAYS — independent of whether
     * {@link #seedDefaultAdmin()} actually runs. Without this, an empty
     * {@code ADMIN_PASSWORD} env var in prod would silently slide past on a
     * pre-seeded DB because the seed path is skipped when the admin already
     * exists. Fail closed at boot so the operator notices immediately.
     */
    @PostConstruct
    void validateAdminPasswordOnStartup() {
        validateAdminPassword();
    }

    private void validateAdminPassword() {
        if (adminPassword == null || adminPassword.length() < MIN_ADMIN_PASSWORD_LENGTH) {
            throw new IllegalStateException(
                "app.bootstrap.admin.password must be set and at least " + MIN_ADMIN_PASSWORD_LENGTH + " characters long. "
                + "In prod, set ADMIN_PASSWORD env var; in dev, configure application-dev.yml."
            );
        }
    }

    @Override
    public void run(ApplicationArguments args) {
        ensureSystemRoles();
        if (userRepository.count() == 0) {
            log.warn("ZERO users detected in database — seeding default admin account.");
            seedDefaultAdmin();
        } else if (!userRepository.existsByUsername(adminUsername)) {
            log.info("Default admin '{}' not found — creating.", adminUsername);
            seedDefaultAdmin();
        }
    }

    private void seedDefaultAdmin() {
        // Defense in depth: re-validate even though @PostConstruct already ran,
        // in case adminPassword was mutated between init and run().
        validateAdminPassword();
        String roleCode = RoleProfileCatalog.definitionForLegacyRole(User.Role.ADMIN).code();
        RoleProfile profile = roleProfileRepository.findByCodeIgnoreCase(roleCode)
                .orElseThrow(() -> new IllegalStateException("Required RoleProfile not found: " + roleCode));

        User user = userRepository.findByUsername(adminUsername)
                .orElseGet(User::new);

        user.setUsername(adminUsername);
        user.setFullName(adminFullName);
        user.setEmail(adminEmail);
        user.setRole(User.Role.ADMIN);
        user.setRoleProfile(profile);
        user.setEnabled(true);
        user.setPassword(passwordEncoder.encode(adminPassword));

        userRepository.save(user);

        log.warn("=== DEFAULT ADMIN CREDENTIALS ===");
        log.warn("  Username: {}", adminUsername);
        log.warn("  Password: <see ADMIN_PASSWORD env var or app.bootstrap.admin.password>");
        log.warn("==================================");
    }

    private void ensureSystemRoles() {
        for (RoleProfileCatalog.SeedDefinition definition : RoleProfileCatalog.seedDefinitions()) {
            roleProfileRepository.findByCodeIgnoreCase(definition.code())
                    .ifPresentOrElse(
                            existing -> {
                                if (!Boolean.TRUE.equals(existing.getIsSystem())) {
                                    existing.setIsSystem(true);
                                    roleProfileRepository.save(existing);
                                }
                            },
                            () -> {
                                RoleProfile role = RoleProfile.builder()
                                        .code(definition.code())
                                        .name(definition.name())
                                        .description(definition.description())
                                        .isSystem(definition.system())
                                        .enabled(true)
                                        .dataScope(definition.dataScope())
                                        .build();
                                role.setMenuPermissions(definition.menuPermissions());
                                role.setAllowedProjects(List.of());
                                role.setAllowedDepts(List.of());
                                roleProfileRepository.save(role);
                            }
                    );
        }
    }
}

