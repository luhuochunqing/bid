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
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Profile("dev")
@RequiredArgsConstructor
@Slf4j
public class LocalDevAccountInitializer implements ApplicationRunner {

    static final String LOCAL_TEST_PASSWORD = "Test@123";

    private final UserRepository userRepository;
    private final RoleProfileRepository roleProfileRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        seedLocalAccounts();
    }

    void seedLocalAccounts() {
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

        accounts.forEach(this::createOrUpdateAccount);
    }

    private void createOrUpdateAccount(LocalAccount account) {
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
        user.setPassword(passwordEncoder.encode(LOCAL_TEST_PASSWORD));

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
