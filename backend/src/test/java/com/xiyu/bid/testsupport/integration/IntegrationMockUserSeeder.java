// Input: UserRepository + 集成测试默认 @WithMockUser 用户名清单
// Output: 保证 H2 测试库中存在与 mock 认证同名的本地用户行
// Pos: TestSupport/集成测试种子
// 维护声明:
//   背景：projectScoped 审计迁移后，@PreAuthorize/@Auditable(ln=true) 链路会经
//   ProjectAccessScopeService.resolveCurrentUser 查库解析当前用户；@WithMockUser 的
//   默认 username("user") 在 H2 中不存在时统一 403「当前用户不存在或不可用」。
//   本类按最小权限原则补种缺失的同名本地用户（非 OSS、enabled）：
//     - 角色一律 MANAGER，绝不给 admin 角色码 —— 负向权限测试（期望 403）不受影响；
//     - ROLE_ADMIN authority 由注解提供，hasAdminAccess 只要求「库内用户存在且非 OSS」，
//       因此正向 ADMIN 用例无需提升种子角色。
//     - 只种默认 mock 名 "user"；自定义 username（如 "admin"、"ai-staff"）由对应测试类
//       自行 ensureSeeded 或自建同名用户，避免与 Flyway 种子/其他测试的 INSERT 撞唯一键。
//   新增自定义 username 的 @WithMockUser 集成测试时，优先在测试内自建同名真实用户
//   （参考 AiDeepCapabilityIntegrationTest#aiEndpoints_ForStaff...），仅当无法自建时再登记到这里。
//   重要约束：只允许写 H2 内存库（jdbc:h2:mem:）。Testcontainers MySQL 容器（FlywayMysqlContainer /
//   AbstractMysqlIntegrationTest 家族）跑的是真实 Flyway 迁移链，其断言（如 V1092 的 role_id NULL 计数）
//   对 users 表数据敏感，种子行会造成跨类污染（回归实证：FlywayMysqlContainerTest 假失败）。
package com.xiyu.bid.testsupport.integration;

import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.List;

@Slf4j
public final class IntegrationMockUserSeeder {

    private static final String MOCK_PASSWORD = "XiyuDemo!2026";

    private static final List<String> DEFAULT_MOCK_USERNAMES = List.of("user");

    private IntegrationMockUserSeeder() {
    }

    public static void ensureSeeded(UserRepository userRepository) {
        for (String username : DEFAULT_MOCK_USERNAMES) {
            ensureSeededFor(userRepository, username);
        }
    }

    /**
     * ApplicationContext 入口：仅当数据源是 H2 内存库时补种，否则静默跳过。
     * 供全局 {@link MockAuthSeedTestExecutionListener} 使用。
     */
    public static void ensureSeededIfH2(ApplicationContext applicationContext) {
        DataSource dataSource;
        try {
            dataSource = applicationContext.getBean(DataSource.class);
        } catch (Exception ignored) {
            return;
        }
        if (!isH2MemDataSource(dataSource)) {
            return;
        }
        try {
            UserRepository userRepository = applicationContext.getBean(UserRepository.class);
            ensureSeeded(userRepository);
        } catch (Exception ignored) {
            // 切片上下文等无 UserRepository 场景
        }
    }

    private static boolean isH2MemDataSource(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            String url = connection.getMetaData().getURL();
            return url != null && url.startsWith("jdbc:h2:mem:");
        } catch (Exception e) {
            log.debug("[test-support] unable to inspect datasource url, skip seeding: {}", e.getMessage());
            return false;
        }
    }

    /** 按单个用户名补种（供测试类对自定义 @WithMockUser username 显式调用）。 */
    public static void ensureSeededFor(UserRepository userRepository, String username) {
        if (userRepository.findByUsername(username).isPresent()) {
            return;
        }
        userRepository.save(User.builder()
                .username(username)
                .password(MOCK_PASSWORD)
                .email(username + "@integration-mock.local")
                .fullName("集成测试 Mock 用户 - " + username)
                .role(User.Role.MANAGER)
                .enabled(true)
                .build());
        log.info("[test-support] seeded integration mock user: {}", username);
    }
}
