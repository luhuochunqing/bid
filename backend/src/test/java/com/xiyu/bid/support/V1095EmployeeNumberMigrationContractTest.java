// Input: a mysql:8.0 Testcontainer with full Flyway chain already applied
// Output: proves V1095 repairs a half-applied employee_number migration state
// Pos: Test/V1095 employee_number 迁移幂等性契约
package com.xiyu.bid.support;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Replays V1095 on top of a schema that already has users.employee_number but
 * lacks idx_users_employee_number. This matches the production failure where
 * Flyway recorded V1095 as failed after hitting "Duplicate column name".
 */
@SpringBootTest(properties = "spring.main.allow-bean-definition-overriding=true")
@ActiveProfiles("flyway-mysql")
@Testcontainers(disabledWithoutDocker = true)
@Import(NoOpPasswordEncryptionTestConfig.class)
class V1095EmployeeNumberMigrationContractTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("xiyu_bid_v1095_replay")
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

    @Test
    void v1095ReplaysWhenEmployeeNumberColumnAlreadyExistsButIndexIsMissing() throws Exception {
        jdbcTemplate.execute("drop index idx_users_employee_number on users");

        String script = new String(
                new ClassPathResource("db/migration-mysql/V1095__add_users_employee_number.sql")
                        .getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);

        for (String statement : splitRespectingDelimiters(script)) {
            String trimmed = statement.trim();
            if (!trimmed.isEmpty()) {
                jdbcTemplate.execute(trimmed);
            }
        }

        Integer columnCount = jdbcTemplate.queryForObject(
                """
                select count(*) from information_schema.COLUMNS
                 where TABLE_SCHEMA = database()
                   and TABLE_NAME = 'users'
                   and COLUMN_NAME = 'employee_number'
                """,
                Integer.class);
        Integer indexCount = jdbcTemplate.queryForObject(
                """
                select count(*) from information_schema.STATISTICS
                 where TABLE_SCHEMA = database()
                   and TABLE_NAME = 'users'
                   and INDEX_NAME = 'idx_users_employee_number'
                   and COLUMN_NAME = 'employee_number'
                """,
                Integer.class);

        assertEquals(1, columnCount);
        assertEquals(1, indexCount);
    }

    private static List<String> splitRespectingDelimiters(String script) {
        List<String> out = new ArrayList<>();
        StringBuilder buf = new StringBuilder();
        String delimiter = ";";
        for (String rawLine : script.split("\\R")) {
            String trimmed = rawLine.trim();
            if (trimmed.toUpperCase().startsWith("DELIMITER ")) {
                if (buf.length() > 0) {
                    out.add(buf.toString());
                    buf.setLength(0);
                }
                delimiter = trimmed.substring("DELIMITER ".length()).trim();
                continue;
            }
            buf.append(rawLine).append('\n');
            if (buf.toString().stripTrailing().endsWith(delimiter)) {
                String statement = buf.toString().stripTrailing();
                statement = statement.substring(0, statement.length() - delimiter.length());
                out.add(statement);
                buf.setLength(0);
            }
        }
        if (buf.length() > 0) {
            out.add(buf.toString());
        }
        return out;
    }
}
