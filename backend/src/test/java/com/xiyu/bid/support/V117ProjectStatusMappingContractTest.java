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
 * V117 迁移回归测试：验证标讯状态扩展与评分分析关联增强在真实 MySQL 上生效。
 *
 * <p>背景：V117（tender_status_expansion）操作的是 <b>tenders.status</b> 列，而非 projects：
 * <ul>
 *   <li>将 tenders.status 从旧枚举（PENDING/TRACKING/BIDDED/ABANDONED）扩展为投标全生命周期枚举
 *       （PENDING_ASSIGNMENT/TRACKING/EVALUATED/BIDDING/WON/LOST/ABANDONED）；</li>
 *   <li>迁移旧值：PENDING → PENDING_ASSIGNMENT、BIDDED → BIDDING；</li>
 *   <li>给 score_analyses 增加 tender_id 列与索引。</li>
 * </ul>
 * 本测试在 Testcontainers MySQL 上执行完整 Flyway 链后，验证 V117 留下的目标状态。
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
            .withPassword("xiyu")
            // 对齐 AbstractMysqlIntegrationTest：sql_mode 去掉 NO_ZERO_DATE/NO_ZERO_IN_DATE（V1077 '0000-00-00' 兼容），collation 对齐 utf8mb4_unicode_ci（V1092 兼容）
            .withCommand(
                    "--character-set-server=utf8mb4",
                    "--collation-server=utf8mb4_unicode_ci",
                    "--sql-mode=ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION");

    @DynamicPropertySource
    static void registerDataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
    }

    private String tendersStatusColumnType() {
        return jdbcTemplate.queryForObject("""
                SELECT COLUMN_TYPE
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'tenders'
                  AND COLUMN_NAME = 'status'
                """, String.class);
    }

    @Test
    void v117_TendersStatusContainsExpandedEnums() {
        String columnType = tendersStatusColumnType();
        assertThat(columnType)
                .as("V117 后 tenders.status 枚举必须包含投标全生命周期目标值")
                .contains("PENDING_ASSIGNMENT", "TRACKING", "EVALUATED", "BIDDING", "WON", "LOST", "ABANDONED");
    }

    @Test
    void v117_TendersStatusDropsLegacyEnums() {
        String columnType = tendersStatusColumnType();
        // 带引号精确匹配独立枚举值，避免误伤 PENDING_ASSIGNMENT（含 'PENDING' 子串）
        assertThat(columnType)
                .as("V117 后旧值 PENDING/BIDDED 必须从 tenders.status 枚举中移除")
                .doesNotContain("'PENDING'", "'BIDDED'");
    }

    @Test
    void v117_ScoreAnalysesHasTenderIdColumn() {
        Integer tenderIdColumnCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'score_analyses'
                  AND COLUMN_NAME = 'tender_id'
                """, Integer.class);
        assertThat(tenderIdColumnCount)
                .as("V117 后 score_analyses 必须存在 tender_id 列")
                .isEqualTo(1);
    }
}