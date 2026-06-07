// Input: a mysql:8.0 Testcontainer with full Flyway chain already applied
// Output: proves V97 is idempotent under replay (simulates Flyway repair + re-run)
// Pos: Test/V97 幂等性契约
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

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Replays V97 on top of an already-migrated schema to prove idempotency.
 * Catches the failure mode where `ALTER TABLE ADD COLUMN` would throw
 * "Duplicate column name" on a Flyway repair + re-run scenario.
 */
@SpringBootTest(properties = "spring.main.allow-bean-definition-overriding=true")
@ActiveProfiles("flyway-mysql")
@Testcontainers(disabledWithoutDocker = true)
@Import(NoOpPasswordEncryptionTestConfig.class)
class V97IdempotencyContractTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("xiyu_bid_v97_replay")
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
    void v97ReplaysWithoutDuplicateColumnError() throws Exception {
        String script = new String(
                new ClassPathResource("db/migration-mysql/V97__workflow_form_designer.sql")
                        .getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);

        // Re-execute the whole V97 script on top of an already-migrated schema.
        // Split on DELIMITER markers so the stored procedure body is submitted as one statement.
        for (String chunk : splitRespectingDelimiters(script)) {
            String trimmed = chunk.trim();
            if (!trimmed.isEmpty()) {
                jdbcTemplate.execute(trimmed);
            }
        }

        // The four added columns must still exist after the replay.
        Integer columnCount = jdbcTemplate.queryForObject(
                """
                select count(*) from information_schema.COLUMNS
                 where TABLE_SCHEMA = database()
                   and TABLE_NAME = 'workflow_form_instances'
                   and COLUMN_NAME in ('template_version','schema_snapshot_json','oa_binding_snapshot_json','oa_payload_json')
                """,
                Integer.class);
        assertEquals(4, columnCount);

        // The helper procedure must not leak into the final schema.
        Integer procedureCount = jdbcTemplate.queryForObject(
                """
                select count(*) from information_schema.ROUTINES
                 where ROUTINE_SCHEMA = database()
                   and ROUTINE_NAME = 'p_add_col_if_missing'
                """,
                Integer.class);
        assertEquals(0, procedureCount);
    }

    /**
     * Naive DELIMITER-aware splitter: honors `DELIMITER $$ ... $$` blocks so that
     * stored procedure bodies are executed as single statements. Statements outside
     * DELIMITER blocks are split on ';'.
     */
    private static java.util.List<String> splitRespectingDelimiters(String script) {
        java.util.List<String> out = new java.util.ArrayList<>();
        StringBuilder buf = new StringBuilder();
        String delimiter = ";";
        for (String rawLine : script.split("\\R")) {
            String line = rawLine;
            String trimmed = line.trim();
            if (trimmed.toUpperCase().startsWith("DELIMITER ")) {
                if (buf.length() > 0) {
                    out.add(buf.toString());
                    buf.setLength(0);
                }
                delimiter = trimmed.substring("DELIMITER ".length()).trim();
                continue;
            }
            buf.append(line).append('\n');
            if (buf.toString().stripTrailing().endsWith(delimiter)) {
                String stmt = buf.toString().stripTrailing();
                stmt = stmt.substring(0, stmt.length() - delimiter.length());
                out.add(stmt);
                buf.setLength(0);
            }
        }
        if (buf.length() > 0) {
            out.add(buf.toString());
        }
        return out;
    }
}
