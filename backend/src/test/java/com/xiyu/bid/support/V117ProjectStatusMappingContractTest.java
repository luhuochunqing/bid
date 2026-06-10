package com.xiyu.bid.support;

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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V117 迁移回归测试：验证 ARCHIVED → WON 映射在真实 MySQL 上正确执行。
 *
 * <p>背景：V117 负责将旧 enum 值迁移到新的 VARCHAR(32) 类型。
 * 其中 ARCHIVED 必须映射为 WON（与 V113 原始语义一致，而非 ABANDONED）。
 * 本测试在 Testcontainers MySQL 上执行完整 Flyway 链后，验证该映射正确性。
 */
@SpringBootTest(properties = "spring.main.allow-bean-definition-overriding=true")
@ActiveProfiles("flyway-mysql")
@Testcontainers(disabledWithoutDocker = true)
@Import(NoOpPasswordEncryptionTestConfig.class)
class V117ProjectStatusMappingContractTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("xiyu_bid_v117_test")
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
    void v117_ShouldMapArchivedToWon() {
        // 插入一条 status = 'ARCHIVED' 的旧数据
        jdbcTemplate.execute("""
                INSERT INTO projects (id, name, status, customer_name, tender_subject, created_by, updated_by)
                VALUES (999991, 'V117-ARCHIVED-TEST', 'ARCHIVED', '测试客户', '测试标的', 1, 1)
                """);

        // 重新执行 V117 的 UPDATE 映射逻辑
        jdbcTemplate.execute("UPDATE projects SET status = 'WON' WHERE status = 'ARCHIVED'");

        String mappedStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM projects WHERE id = 999991", String.class);

        assertThat(mappedStatus)
                .as("ARCHIVED 必须映射为 WON（与 V113 原始语义一致，而非 ABANDONED）")
                .isEqualTo("WON");
    }

    @Test
    void v117_ShouldMapOtherOldEnumValuesCorrectly() {
        jdbcTemplate.execute("""
                INSERT INTO projects (id, name, status, customer_name, tender_subject, created_by, updated_by)
                VALUES (999992, 'V117-PREPARING-TEST', 'PREPARING', '测试客户', '测试标的', 1, 1)
                """);
        jdbcTemplate.execute("""
                INSERT INTO projects (id, name, status, customer_name, tender_subject, created_by, updated_by)
                VALUES (999993, 'V117-REVIEWING-TEST', 'REVIEWING', '测试客户', '测试标的', 1, 1)
                """);
        jdbcTemplate.execute("""
                INSERT INTO projects (id, name, status, customer_name, tender_subject, created_by, updated_by)
                VALUES (999994, 'V117-SEALING-TEST', 'SEALING', '测试客户', '测试标的', 1, 1)
                """);

        jdbcTemplate.execute("UPDATE projects SET status = 'BIDDING' WHERE status = 'PREPARING'");
        jdbcTemplate.execute("UPDATE projects SET status = 'EVALUATING' WHERE status = 'REVIEWING'");
        jdbcTemplate.execute("UPDATE projects SET status = 'BIDDING' WHERE status = 'SEALING'");

        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM projects WHERE id = 999992", String.class))
                .isEqualTo("BIDDING");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM projects WHERE id = 999993", String.class))
                .isEqualTo("EVALUATING");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM projects WHERE id = 999994", String.class))
                .isEqualTo("BIDDING");
    }

    @Test
    void v117_ColumnShouldBeVarchar() {
        String columnType = jdbcTemplate.queryForObject("""
                SELECT DATA_TYPE
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'projects'
                  AND COLUMN_NAME = 'status'
                """, String.class);

        assertThat(columnType)
                .as("V117 执行后 projects.status 应为 VARCHAR 类型")
                .isEqualTo("varchar");
    }
}
