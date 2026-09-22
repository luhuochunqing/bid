package com.xiyu.bid.scoreparse.application;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
class ScoreParseEvidenceMigrationMysqlTest {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("score_evidence_migration")
            .withUsername("xiyu")
            .withPassword("xiyu");

    @Test
    void migrationPreservesLongExplanationAndRollbackRejectsTruncation() throws Exception {
        try (Connection connection = DriverManager.getConnection(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE score_parse_evidence (id BIGINT PRIMARY KEY, explanation VARCHAR(255) NULL)");
            executeMigration(statement, "db/migration-mysql/V1216__widen_score_parse_evidence_explanation.sql");
            String longText = "评分依据".repeat(125);
            try (var insert = connection.prepareStatement(
                    "INSERT INTO score_parse_evidence (id, explanation) VALUES (1, ?)")) {
                insert.setString(1, longText);
                insert.executeUpdate();
            }
            assertThat(readExplanation(statement)).isEqualTo(longText);
            assertThat(columnType(statement)).isEqualTo("text");
            String modeBeforeRollback = sessionMode(statement);

            assertThatThrownBy(() -> executeMigration(statement,
                    "db/rollback/migration-mysql/U1216__widen_score_parse_evidence_explanation.sql"))
                    .as("超过 255 字时回滚必须失败，不得截断数据")
                    .isInstanceOf(java.sql.SQLException.class);
            assertThat(columnType(statement)).isEqualTo("text");
            assertThat(readExplanation(statement)).isEqualTo(longText);
            assertThat(sessionMode(statement)).isEqualTo(modeBeforeRollback);

            statement.execute("UPDATE score_parse_evidence SET explanation = 'short' WHERE id = 1");
            executeMigration(statement, "db/rollback/migration-mysql/U1216__widen_score_parse_evidence_explanation.sql");
            assertThat(columnType(statement)).isEqualTo("varchar");
            assertThat(readExplanation(statement)).isEqualTo("short");
            assertThat(sessionMode(statement)).isEqualTo(modeBeforeRollback);
        }
    }

    private static void executeMigration(Statement statement, String resource) throws Exception {
        try (var input = ScoreParseEvidenceMigrationMysqlTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertThat(input).as(resource).isNotNull();
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8)
                    .replaceAll("(?m)^--[^\\n]*$", "");
            for (String command : sql.split(";")) {
                if (!command.isBlank()) statement.execute(command);
            }
        }
    }

    private static String readExplanation(Statement statement) throws Exception {
        try (ResultSet result = statement.executeQuery("SELECT explanation FROM score_parse_evidence WHERE id = 1")) {
            assertThat(result.next()).isTrue();
            return result.getString(1);
        }
    }

    private static String columnType(Statement statement) throws Exception {
        try (ResultSet result = statement.executeQuery("SELECT DATA_TYPE FROM information_schema.COLUMNS "
                + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'score_parse_evidence' AND COLUMN_NAME = 'explanation'")) {
            assertThat(result.next()).isTrue();
            return result.getString(1);
        }
    }

    private static String sessionMode(Statement statement) throws Exception {
        try (ResultSet result = statement.executeQuery("SELECT @@SESSION.sql_mode")) {
            assertThat(result.next()).isTrue();
            return result.getString(1);
        }
    }
}
