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
 * V1102 迁移回归测试：验证 business_qualifications.status 从 MySQL ENUM 改为 VARCHAR(32) 后，
 * 可正常写入 Java 枚举定义的全部状态值（IN_STOCK / EXPIRING / EXPIRED / RETIRED / VALID）。
 *
 * <p>背景（CO-358）：B73 baseline 建表时 status 列为 enum('VALID','EXPIRING','EXPIRED')，
 * 但 Java 枚举 QualificationStatus 有 IN_STOCK / RETIRED。retire 接口写 RETIRED 时被
 * MySQL enum 列拒绝（"Data truncated for column 'status'"）→ 500。V1102 把列改为 VARCHAR(32)
 * 彻底解决。本测试在 Testcontainers MySQL 上执行完整 Flyway 链后，验证 schema 与数据写入。
 */
@SpringBootTest(properties = "spring.main.allow-bean-definition-overriding=true")
@ActiveProfiles("flyway-mysql")
@Testcontainers(disabledWithoutDocker = true)
@Import(NoOpPasswordEncryptionTestConfig.class)
class V1102QualificationStatusEnumToVarcharContractTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("xiyu_bid_v1102_test")
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
    void v1102_ColumnShouldBeVarcharNotEnum() {
        String dataType = jdbcTemplate.queryForObject("""
                SELECT DATA_TYPE
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'business_qualifications'
                  AND COLUMN_NAME = 'status'
                """, String.class);

        assertThat(dataType)
                .as("V1102 执行后 business_qualifications.status 应为 VARCHAR 类型，不再是 enum")
                .isEqualTo("varchar");
    }

    @Test
    void v1102_ColumnLengthShouldBe32() {
        Long charLength = jdbcTemplate.queryForObject("""
                SELECT CHARACTER_MAXIMUM_LENGTH
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'business_qualifications'
                  AND COLUMN_NAME = 'status'
                """, Long.class);

        assertThat(charLength)
                .as("V1102 执行后 business_qualifications.status 长度应为 32，对齐实体 @Column(length=32)")
                .isEqualTo(32L);
    }

    @Test
    void v1102_CanInsertRetiredStatusWithoutDataTruncation() {
        // CO-358 核心验证：RETIRED 必须能写入，不再被 enum 列拒绝
        jdbcTemplate.update("""
                INSERT INTO business_qualifications
                  (id, name, subject_type, subject_name, category, status, retired,
                   reminder_enabled, reminder_days, created_at, updated_at)
                VALUES
                  (999991, 'V1102-RETIRED-TEST', 'COMPANY', '测试主体', 'OTHER', 'RETIRED', true,
                   false, 30, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
                """);

        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM business_qualifications WHERE id = 999991", String.class);

        assertThat(status)
                .as("RETIRED 状态必须能成功写入——这是 CO-358 retire 接口 500 的根因")
                .isEqualTo("RETIRED");
    }

    @Test
    void v1102_CanInsertInStockStatusWithoutDataTruncation() {
        // V1075 的隐患也由 V1102 解决：IN_STOCK 必须能写入
        jdbcTemplate.update("""
                INSERT INTO business_qualifications
                  (id, name, subject_type, subject_name, category, status, retired,
                   reminder_enabled, reminder_days, created_at, updated_at)
                VALUES
                  (999992, 'V1102-IN-STOCK-TEST', 'COMPANY', '测试主体', 'OTHER', 'IN_STOCK', false,
                   false, 30, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
                """);

        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM business_qualifications WHERE id = 999992", String.class);

        assertThat(status)
                .as("IN_STOCK 状态必须能成功写入——V1075 迁移的前提条件")
                .isEqualTo("IN_STOCK");
    }

    @Test
    void v1102_LegacyEnumValuesPreserved() {
        // 现有数据无损：VALID/EXPIRING/EXPIRED 仍可正常读写
        for (String legacy : new String[]{"VALID", "EXPIRING", "EXPIRED"}) {
            long id = 999993L + legacy.hashCode();
            jdbcTemplate.update("""
                    INSERT INTO business_qualifications
                      (id, name, subject_type, subject_name, category, status, retired,
                       reminder_enabled, reminder_days, created_at, updated_at)
                    VALUES
                      (?, ?, 'COMPANY', '测试主体', 'OTHER', ?, false,
                       false, 30, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
                    """, id, "V1102-" + legacy + "-TEST", legacy);

            String status = jdbcTemplate.queryForObject(
                    "SELECT status FROM business_qualifications WHERE id = ?", String.class, id);

            assertThat(status)
                    .as("旧 enum 值 %s 在 VARCHAR 列中必须保持不变", legacy)
                    .isEqualTo(legacy);
        }
    }

    @Test
    void v1102_CanUpdateRetiredStatusViaJpqlLikeUpdate() {
        // 模拟 PR #1150 的 JPQL UPDATE 路径：UPDATE ... SET status = 'RETIRED' WHERE id = ?
        jdbcTemplate.update("""
                INSERT INTO business_qualifications
                  (id, name, subject_type, subject_name, category, status, retired,
                   reminder_enabled, reminder_days, created_at, updated_at)
                VALUES
                  (999994, 'V1102-UPDATE-TEST', 'COMPANY', '测试主体', 'OTHER', 'IN_STOCK', false,
                   false, 30, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
                """);

        // 模拟 retire 操作的 UPDATE
        jdbcTemplate.update("""
                UPDATE business_qualifications
                   SET status = 'RETIRED', retired = true, updated_at = CURRENT_TIMESTAMP(6)
                 WHERE id = 999994
                """);

        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM business_qualifications WHERE id = 999994", String.class);
        Boolean retired = jdbcTemplate.queryForObject(
                "SELECT retired FROM business_qualifications WHERE id = 999994", Boolean.class);

        assertThat(status).isEqualTo("RETIRED");
        assertThat(retired).isTrue();
    }
}
