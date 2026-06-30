package com.xiyu.bid.support;

import com.xiyu.bid.contractborrow.domain.valueobject.ContractBorrowEventType;
import com.xiyu.bid.contractborrow.domain.valueobject.ContractBorrowStatus;
import com.xiyu.bid.contractborrow.infrastructure.persistence.entity.ContractBorrowApplicationEntity;
import com.xiyu.bid.contractborrow.infrastructure.persistence.entity.ContractBorrowEventEntity;
import com.xiyu.bid.contractborrow.infrastructure.persistence.repository.ContractBorrowApplicationJpaRepository;
import com.xiyu.bid.contractborrow.infrastructure.persistence.repository.ContractBorrowEventJpaRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(properties = "spring.main.allow-bean-definition-overriding=true")
@ActiveProfiles("flyway-mysql")
// 不使用 disabledWithoutDocker=true：Docker 不可用时必须 fail-fast，
// 而非静默跳过。否则 BUILD SUCCESS 会给人虚假安全感（PR #1367 的根因之一）。
@Testcontainers
@Import(NoOpPasswordEncryptionTestConfig.class)
class FlywayMysqlContainerTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ContractBorrowApplicationJpaRepository contractBorrowApplications;

    @Autowired
    private ContractBorrowEventJpaRepository contractBorrowEvents;

    @Autowired
    private EntityManager entityManager;

    // 生产 sql_mode 已确认（2026-06-30，通过 SSH jetty@172.16.38.78 直连 RDS 查询）：
    //   @@sql_mode = ''（空字符串，所有 strict mode 关闭）
    //   @@version  = 8.0.43-251200 (MySQL Community Server - GPL)
    // 结论：
    //   1. V1077 的 '0000-00-00 00:00:00' 字面量在生产合法（sql_mode 空不阻止零日期）
    //   2. 不需要新增 V1114+ 修正迁移（V1077 在生产运行正常）
    //   3. V1077 已合入受 Flyway checksum 保护，不可修改
    //
    // 已知债务（TODO(test-prod-sql-mode-alignment)）：
    //   生产 sql_mode 为空，比测试侧宽松。AbstractMysqlIntegrationTest 保留
    //   ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION。
    //   这意味着测试能跑过的 SQL 在生产更宽松环境下也能跑（无问题），
    //   但反过来生产能跑的某些"不严格"SQL 在测试会被拒绝（潜在漏测）。
    //   完整对齐需要先审计生产数据中是否存在零日期/截断字符串等问题，
    //   再决定是在生产开启严格模式还是在测试进一步放宽 sql_mode。
    //   独立运维任务，不在本 PR 范围内。
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("xiyu_bid_test")
            .withUsername("xiyu")
            .withPassword("xiyu");

    @DynamicPropertySource
    static void registerDataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
    }

    @Test
    void contextLoadsWithFlywayBaselineOnRealMysql() {
        Integer baselineRuns = jdbcTemplate.queryForObject(
                """
                select count(*)
                from flyway_schema_history
                where success = 1
                  and version = '73'
                  and script = 'B73__full_schema_baseline.sql'
                """,
                Integer.class
        );

        Integer projectQualityTableCount = jdbcTemplate.queryForObject(
                """
                select count(*)
                from information_schema.tables
                where table_schema = database()
                  and table_name = 'project_quality_checks'
                """,
                Integer.class
        );

        Integer tenderAssignmentTableCount = jdbcTemplate.queryForObject(
                """
                select count(*)
                from information_schema.tables
                where table_schema = database()
                  and table_name = 'tender_assignment_records'
                """,
                Integer.class
        );

        Integer roleSeedCount = jdbcTemplate.queryForObject(
                """
                select count(*)
                from roles
                where code in ('admin', 'manager', 'staff')
                """,
                Integer.class
        );

        Integer legacyIncrementalRuns = jdbcTemplate.queryForObject(
                """
                select count(*)
                from flyway_schema_history
                where script = 'V1__resources_contracts.sql'
                """,
                Integer.class
        );

        assertEquals(1, baselineRuns);
        assertEquals(1, projectQualityTableCount);
        assertEquals(1, tenderAssignmentTableCount);
        assertEquals(3, roleSeedCount);
        assertEquals(0, legacyIncrementalRuns);
    }

    @Test
    void v1092RoleMigrationPreservesBidAdmin() {
        // V1092 迁移后验证：bid_admin 应该被迁移为 /bidAdmin，且用户 role_id 不应为 NULL
        Integer bidAdminRoleCount = jdbcTemplate.queryForObject(
                """
                select count(*)
                from roles
                where code = '/bidAdmin'
                """,
                Integer.class
        );

        Integer legacyBidAdminCount = jdbcTemplate.queryForObject(
                """
                select count(*)
                from roles
                where code = 'bid_admin'
                """,
                Integer.class
        );

        Integer usersWithNullRoleId = jdbcTemplate.queryForObject(
                "select count(*) from users where role_id is null",
                Integer.class
        );

        assertEquals(1, bidAdminRoleCount, "/bidAdmin role should exist after V1092");
        assertEquals(0, legacyBidAdminCount, "bid_admin role should not exist after V1092");
        assertEquals(0, usersWithNullRoleId, "No users should have NULL role_id after migration");
    }

    @Test
    void v1092MergesUsersWhenTargetRoleAlreadyExists() {
        // 核心分支验证：当 /bidAdmin 已存在且 bid_admin 也存在时，
        // V1092 必须将 bid_admin 下的用户合并到 /bidAdmin，然后删除 bid_admin。
        // 利用 V1092 脚本的幂等性，在当前已迁移的数据上人工构造该场景并重新执行迁移。

        // 1. 找到当前 /bidAdmin 的角色 ID
        Long targetRoleId = jdbcTemplate.queryForObject(
                "select id from roles where code = '/bidAdmin'",
                Long.class
        );

        // 2. 人工重建 bid_admin 角色（模拟迁移前状态）
        jdbcTemplate.update(
                """
                insert into roles (code, name, description, is_system, enabled, data_scope, menu_permissions, created_at, updated_at)
                values ('bid_admin', 'Legacy Bid Admin', 'legacy', true, true, 'all', 'all', current_timestamp(6), current_timestamp(6))
                """
        );
        Long legacyRoleId = jdbcTemplate.queryForObject(
                "select id from roles where code = 'bid_admin'",
                Long.class
        );

        // 3. 创建一个绑定到 legacy bid_admin 的用户
        jdbcTemplate.update(
                """
                insert into users (username, full_name, email, password, enabled, email_verified, role, role_id, created_at, updated_at)
                values ('v1092-merge-test', 'V1092 Merge Test', 'v1092-merge@xiyu.local', 'noop', true, true, 'MANAGER', ?, current_timestamp(6), current_timestamp(6))
                """,
                legacyRoleId
        );

        // 4. 重新执行 V1092 迁移脚本（幂等）
        String v1092Script = loadMigrationScript("db/migration-mysql/V1092__migrate_legacy_role_codes_to_oss_aligned.sql");
        jdbcTemplate.execute(v1092Script);

        // 5. 验证：legacy 角色被删除，用户已合并到 /bidAdmin
        Integer legacyRoleCount = jdbcTemplate.queryForObject(
                "select count(*) from roles where code = 'bid_admin'",
                Integer.class
        );

        Long mergedUserRoleId = jdbcTemplate.queryForObject(
                "select role_id from users where username = 'v1092-merge-test'",
                Long.class
        );

        assertEquals(0, legacyRoleCount, "Legacy bid_admin role should be removed after merge");
        assertEquals(targetRoleId, mergedUserRoleId, "User should be merged into existing /bidAdmin role");
    }

    private String loadMigrationScript(String location) {
        try (var input = getClass().getClassLoader().getResourceAsStream(location)) {
            if (input == null) {
                throw new IllegalStateException("Migration script not found: " + location);
            }
            return new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Failed to read migration script: " + location, e);
        }
    }

    @Test
    void contractBorrowEnumsRoundTripThroughJpaOnMysql() {
        ContractBorrowApplicationEntity application = contractBorrowApplications.saveAndFlush(
                ContractBorrowApplicationEntity.builder()
                        .contractNo("HT-MYSQL-001")
                        .contractName("MySQL enum smoke contract")
                        .borrowerName("mysql-smoke")
                        .submittedAt(LocalDateTime.now())
                        .status(ContractBorrowStatus.PENDING_APPROVAL)
                        .build()
        );

        ContractBorrowEventEntity event = contractBorrowEvents.saveAndFlush(
                ContractBorrowEventEntity.builder()
                        .applicationId(application.getId())
                        .eventType(ContractBorrowEventType.SUBMITTED)
                        .statusAfter(ContractBorrowStatus.PENDING_APPROVAL)
                        .build()
        );

        entityManager.clear();

        ContractBorrowApplicationEntity reloadedApplication = contractBorrowApplications
                .findById(application.getId())
                .orElseThrow();
        ContractBorrowEventEntity reloadedEvent = contractBorrowEvents.findById(event.getId()).orElseThrow();

        assertEquals(ContractBorrowStatus.PENDING_APPROVAL, reloadedApplication.getStatus());
        assertEquals(ContractBorrowEventType.SUBMITTED, reloadedEvent.getEventType());
        assertEquals(ContractBorrowStatus.PENDING_APPROVAL, reloadedEvent.getStatusAfter());
    }
}
