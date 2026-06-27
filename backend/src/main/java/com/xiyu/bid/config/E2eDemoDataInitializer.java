// Input: Spring 配置属性、环境变量、外部 bean 依赖
// Output: 配置 Bean、过滤器、线程池和启动级常量
// Pos: Config/基础设施层
// 维护声明: 仅维护配置与启动约束；业务规则变更请同步到对应 service/controller.
package com.xiyu.bid.config;

import com.xiyu.bid.entity.RoleProfile;
import com.xiyu.bid.entity.RoleProfileCatalog;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.RoleProfileRepository;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.task.entity.TaskStatusCategory;
import com.xiyu.bid.task.entity.TaskStatusDict;
import com.xiyu.bid.task.repository.TaskStatusDictRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Profile("e2e")
@RequiredArgsConstructor
@Slf4j
public class E2eDemoDataInitializer implements ApplicationRunner {

    private static final String DEMO_PASSWORD = "123456";

    private final UserRepository userRepository;
    private final RoleProfileRepository roleProfileRepository;
    private final TaskStatusDictRepository taskStatusDictRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        ensureSystemRoles();
        seedTaskStatuses();
        seedDemoUsers();
    }

    void seedTaskStatuses() {
        // CO-361: 三态模型收口——不再种入 IN_PROGRESS 字典行
        List<TaskStatusSeed> seeds = List.of(
                new TaskStatusSeed("TODO", "待办", TaskStatusCategory.OPEN, "#909399", 10, true, false),
                new TaskStatusSeed("REVIEW", "待审核", TaskStatusCategory.REVIEW, "#e6a23c", 30, false, false),
                new TaskStatusSeed("COMPLETED", "已完成", TaskStatusCategory.CLOSED, "#67c23a", 40, false, true)
        );

        seeds.forEach(seed -> {
            TaskStatusDict status = taskStatusDictRepository.findById(seed.code())
                    .orElseGet(TaskStatusDict::new);
            status.setCode(seed.code());
            status.setName(seed.name());
            status.setCategory(seed.category());
            status.setColor(seed.color());
            status.setSortOrder(seed.sortOrder());
            status.setIsInitial(seed.initial());
            status.setIsTerminal(seed.terminal());
            status.setEnabled(true);
            taskStatusDictRepository.save(status);
        });
    }

    void seedDemoUsers() {
        List<DemoUser> demoUsers = List.of(
                new DemoUser("lizong", "李总", "lizong@example.com", RoleProfileCatalog.ADMIN_CODE),
                new DemoUser("xiaowang", "小王", "xiaowang@example.com", RoleProfileCatalog.BID_SPECIALIST_CODE),
                new DemoUser("xiaochen", "陈投标管理", "xiaochen@example.com", RoleProfileCatalog.BID_ADMIN_CODE),
                new DemoUser("xiaoliu", "刘投标组长", "xiaoliu@example.com", RoleProfileCatalog.BID_LEAD_CODE),
                new DemoUser("xiaozhang", "张销售", "xiaozhang@example.com", RoleProfileCatalog.SALES_CODE),
                new DemoUser("xiaozhou", "周投标专员", "xiaozhou@example.com", RoleProfileCatalog.BID_SPECIALIST_CODE),
                new DemoUser("xiaozheng", "郑行政", "xiaozheng@example.com", RoleProfileCatalog.ADMIN_STAFF_CODE)
        );

        demoUsers.forEach(this::createOrUpdateUser);
    }

    private void createOrUpdateUser(DemoUser demoUser) {
        User user = userRepository.findByUsername(demoUser.username())
                .orElseGet(User::new);

        var profile = roleProfileRepository.findByCodeIgnoreCase(demoUser.roleCode())
                .orElseThrow(() -> new IllegalStateException("Required RoleProfile not found: " + demoUser.roleCode()));

        user.setUsername(demoUser.username());
        user.setFullName(demoUser.fullName());
        user.setEmail(demoUser.email());
        user.setRole(RoleProfileCatalog.legacyRoleForCode(demoUser.roleCode()));
        user.setRoleProfile(profile);
        user.setEnabled(true);
        user.setPassword(passwordEncoder.encode(DEMO_PASSWORD));

        userRepository.save(user);
        log.info("Seeded e2e demo user: {}", demoUser.username());
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

    private record DemoUser(String username, String fullName, String email, String roleCode) {
    }

    private record TaskStatusSeed(String code, String name, TaskStatusCategory category,
                                  String color, int sortOrder, boolean initial, boolean terminal) {
    }
}
