package com.xiyu.bid.scoreparse.application;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
class ScoreParseTaskLockMysqlTest {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("score_task_lock")
            .withUsername("xiyu")
            .withPassword("xiyu");

    @Test
    void newParseTaskAndAdoptionReadSerializeOnProjectRow() throws Exception {
        try (Connection setup = connection(); var statement = setup.createStatement()) {
            statement.execute("CREATE TABLE projects (id BIGINT PRIMARY KEY)");
            statement.execute("CREATE TABLE score_parse_task (id BIGINT PRIMARY KEY, project_id BIGINT, task_type VARCHAR(20))");
            statement.execute("INSERT INTO projects VALUES (100)");
            statement.execute("INSERT INTO score_parse_task VALUES (9, 100, 'PARSE')");
        }
        try (Connection creator = connection(); var executor = Executors.newSingleThreadExecutor()) {
            creator.setAutoCommit(false);
            creator.createStatement().executeQuery("SELECT id FROM projects WHERE id = 100 FOR UPDATE").close();
            CountDownLatch waitingForLock = new CountDownLatch(1);
            var latestAfterLock = executor.submit(() -> {
                try (Connection adopter = connection(); var statement = adopter.createStatement()) {
                    adopter.setAutoCommit(false);
                    // 建立 REPEATABLE READ 旧快照，随后必须靠锁定当前读看到新任务。
                    statement.executeQuery("SELECT COUNT(*) FROM score_parse_task").close();
                    waitingForLock.countDown();
                    statement.executeQuery("SELECT id FROM projects WHERE id = 100 FOR UPDATE").close();
                    try (var result = statement.executeQuery("SELECT id FROM score_parse_task "
                            + "WHERE project_id = 100 AND task_type = 'PARSE' ORDER BY id DESC LIMIT 1 FOR UPDATE")) {
                        assertThat(result.next()).isTrue();
                        long latestId = result.getLong(1);
                        adopter.commit();
                        return latestId;
                    }
                }
            });
            assertThat(waitingForLock.await(5, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> latestAfterLock.get(300, TimeUnit.MILLISECONDS))
                    .as("采纳必须等待新任务创建事务提交")
                    .isInstanceOf(TimeoutException.class);
            creator.createStatement().execute("INSERT INTO score_parse_task VALUES (10, 100, 'PARSE')");
            creator.commit();
            assertThat(latestAfterLock.get(10, TimeUnit.SECONDS)).isEqualTo(10L);
        }
    }

    private static Connection connection() throws Exception {
        return DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
    }
}
