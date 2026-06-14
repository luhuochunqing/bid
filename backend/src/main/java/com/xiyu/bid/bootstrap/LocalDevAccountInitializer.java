// Input: local dev Spring profile, user repository, role repository, password encoder
// Output: local development login accounts aligned with frontend login hints
// Pos: Bootstrap/本地开发账号初始化
// 维护声明: 仅维护 dev profile 的本地联调账号；生产账号、权限规则和业务数据请勿放入这里。
package com.xiyu.bid.bootstrap;

import com.xiyu.bid.entity.RoleProfile;
import com.xiyu.bid.entity.RoleProfileCatalog;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.RoleProfileRepository;
import com.xiyu.bid.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Profile("dev")
@ConditionalOnProperty(
        name = "app.bootstrap.local-dev.enabled",
        havingValue = "true",
        matchIfMissing = false)
@RequiredArgsConstructor
@Slf4j
@Order(1)
public class LocalDevAccountInitializer implements ApplicationRunner {

    /**
     * Env var name carrying the local dev password. When unset (or blank),
     * the initializer logs a warning and skips seeding to avoid shipping
     * a default password to dev/e2e environments.
     */
    static final String LOCAL_DEV_PASSWORD_ENV = "LOCAL_DEV_PASSWORD";

    /** System property equivalent of {@link #LOCAL_DEV_PASSWORD_ENV} (used in tests). */
    static final String LOCAL_DEV_PASSWORD_PROPERTY = "app.bootstrap.local-dev.password";

    /** Fallback password used only when LOCAL_DEV_PASSWORD is explicitly set. */
    static final String LOCAL_TEST_PASSWORD = "Test@123";

    private final UserRepository userRepository;
    private final RoleProfileRepository roleProfileRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        seedLocalAccounts();
    }

    void seedLocalAccounts() {
        String password = resolveLocalDevPassword();
        if (password == null) {
            return;
        }
        List<LocalAccount> accounts = List.of(
                new LocalAccount("staff", "小王", "staff@xiyu-local", RoleProfileCatalog.STAFF_CODE),
                new LocalAccount("manager", "张经理", "manager@xiyu-local", RoleProfileCatalog.MANAGER_CODE),
                new LocalAccount("auditor", "赵审计", "auditor@xiyu-local", RoleProfileCatalog.AUDITOR_CODE),
                new LocalAccount("bid_admin", "陈投标管理", "bidadmin@xiyu-local", RoleProfileCatalog.BID_ADMIN_CODE),
                new LocalAccount("bid_lead", "刘投标组长", "bidlead@xiyu-local", RoleProfileCatalog.BID_LEAD_CODE),
                new LocalAccount("sales", "张销售", "sales@xiyu-local", RoleProfileCatalog.SALES_CODE),
                new LocalAccount("task_executor", "吴执行", "taskexecutor@xiyu-local", RoleProfileCatalog.TASK_EXECUTOR_CODE),
                new LocalAccount("bid_specialist", "周投标专员", "bidspecialist@xiyu-local", RoleProfileCatalog.BID_SPECIALIST_CODE),
                new LocalAccount("admin_staff", "郑行政", "adminstaff@xiyu-local", RoleProfileCatalog.ADMIN_STAFF_CODE)
        );

        accounts.forEach(a -> createOrUpdateAccount(a, password));
    }

    /**
     * Resolve the dev password from {@link #LOCAL_DEV_PASSWORD_ENV}
     * (env var) or {@link #LOCAL_DEV_PASSWORD_PROPERTY} (system property,
     * useful in tests). Returns null when both are unset/blank — the
     * caller must skip seeding.
     */
    String resolveLocalDevPassword() {
        String env = System.getenv(LOCAL_DEV_PASSWORD_ENV);
        if (env == null || env.isBlank()) {
            env = System.getProperty(LOCAL_DEV_PASSWORD_PROPERTY);
        }
        if (env == null || env.isBlank()) {
            log.warn("LOCAL_DEV_PASSWORD not set; skipping local dev account seeding."
                    + " Set {} (env) or -D{} (system property) to opt in."
                    + " (app.bootstrap.local-dev.enabled=true)",
                    LOCAL_DEV_PASSWORD_ENV, LOCAL_DEV_PASSWORD_PROPERTY);
            return null;
        }
        return env;
    }

    private void createOrUpdateAccount(LocalAccount account, String password) {
        RoleProfile roleProfile = resolveRoleProfile(account.roleCode());
        User user = userRepository.findByUsername(account.username())
                .orElseGet(User::new);

        user.setUsername(account.username());
        user.setFullName(account.fullName());
        user.setEmail(account.email());
        user.setRole(RoleProfileCatalog.legacyRoleForCode(account.roleCode()));
        user.setRoleProfile(roleProfile);
        user.setEnabled(true);
        user.setEmailVerified(true);
        user.setPassword(passwordEncoder.encode(password));

        userRepository.save(user);
        log.info("Ensured local dev account: {}", account.username());
    }

    private RoleProfile resolveRoleProfile(String roleCode) {
        return roleProfileRepository.findByCodeIgnoreCase(roleCode)
                .orElseThrow(() -> new IllegalStateException("Required RoleProfile not found: " + roleCode));
    }

    private record LocalAccount(String username, String fullName, String email, String roleCode) {
    }
}
