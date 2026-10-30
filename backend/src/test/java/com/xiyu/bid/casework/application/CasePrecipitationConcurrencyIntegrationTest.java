package com.xiyu.bid.casework.application;

import com.xiyu.bid.casework.infrastructure.CasePrecipitationTaskRepository;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.repository.ProjectRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(CasePrecipitationTaskStateService.class)
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CasePrecipitationConcurrencyIntegrationTest {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("case_precipitation_concurrency").withUsername("xiyu").withPassword("xiyu");

    @DynamicPropertySource
    static void mysql(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
    }

    @Autowired CasePrecipitationTaskStateService service;
    @Autowired CasePrecipitationTaskRepository taskRepository;
    @Autowired ProjectRepository projectRepository;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired TransactionTemplate transactionTemplate;

    @Test
    void threeConcurrentClaimsReturnOneTask() throws Exception {
        Project project = Project.builder().name("并发大文件项目").tenderId(9001L).managerId(8001L).build();
        Long projectId = projectRepository.saveAndFlush(project).getId();
        var ready = new CountDownLatch(3);
        var start = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(3)) {
            List<Future<CasePrecipitationTaskStateService.ClaimResult>> futures = java.util.stream.IntStream.range(0, 3)
                    .mapToObj(i -> pool.submit(() -> { ready.countDown(); start.await(); return service.claim(projectId, 7000L + i); }))
                    .toList();
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<CasePrecipitationTaskStateService.ClaimResult> results = futures.stream().map(this::get).toList();
            assertThat(results).extracting(r -> r.task().taskId()).containsOnly(results.getFirst().task().taskId());
            assertThat(results).filteredOn(CasePrecipitationTaskStateService.ClaimResult::reused).hasSize(2);
            assertThat(taskRepository.count()).isEqualTo(1);
        }
    }

    @Test
    void staleTaskCannotCompleteAfterReplacementTaskIsClaimed() {
        Project project = Project.builder().name("迟到结果围栏项目").tenderId(9002L).managerId(8002L).build();
        Long projectId = projectRepository.saveAndFlush(project).getId();
        var first = service.claim(projectId, 7001L);
        assertThat(service.start(first.task().taskId())).isTrue();
        jdbcTemplate.update("UPDATE case_precipitation_task SET updated_at = DATE_SUB(NOW(), INTERVAL 3 HOUR) WHERE task_id = ?",
                first.task().taskId());

        var replacement = service.claim(projectId, 7002L);

        assertThat(replacement.reused()).isFalse();
        assertThat(replacement.task().taskId()).isNotEqualTo(first.task().taskId());
        assertThat(service.complete(first.task().taskId(), 9)).isFalse();
        assertThat(service.start(replacement.task().taskId())).isTrue();
        assertThat(service.complete(replacement.task().taskId(), 2)).isTrue();
    }

    @Test
    void completedStateRollsBackWhenLaterPersistenceConstraintFails() {
        Project project = Project.builder().name("原子提交项目").tenderId(9003L).managerId(8003L).build();
        Long projectId = projectRepository.saveAndFlush(project).getId();
        var claimed = service.claim(projectId, 7003L);
        assertThat(service.start(claimed.task().taskId())).isTrue();

        org.junit.jupiter.api.Assertions.assertThrows(DataIntegrityViolationException.class,
                () -> transactionTemplate.executeWithoutResult(status -> {
                    assertThat(service.complete(claimed.task().taskId(), 1)).isTrue();
                    jdbcTemplate.update("INSERT INTO case_precipitation_task(task_id, project_id, status, created_at, updated_at) VALUES (?, NULL, 'PENDING', NOW(), NOW())",
                            "invalid-follow-up-write");
                }));

        assertThat(taskRepository.findByTaskId(claimed.task().taskId()).orElseThrow().getStatus())
                .isEqualTo("PROCESSING");
        service.fail(claimed.task().taskId(), "案例保存失败，请联系管理员");
        assertThat(taskRepository.findByTaskId(claimed.task().taskId()).orElseThrow().getStatus())
                .isEqualTo("FAILED");
    }

    @Test
    void failureReasonSurvivesMysqlPersistenceAndLatestTaskRead() {
        Project project = Project.builder().name("失败原因恢复项目").tenderId(9004L).managerId(8004L).build();
        Long projectId = projectRepository.saveAndFlush(project).getId();
        var claimed = service.claim(projectId, 7004L);
        service.start(claimed.task().taskId());
        String reason = CasePrecipitationFailureMessages.from(
                new com.xiyu.bid.scoreparse.application.BidFileQuotaException(1603796578L, 1073741824L));
        service.fail(claimed.task().taskId(), reason);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT user_error_message FROM case_precipitation_task WHERE task_id = ?", String.class,
                claimed.task().taskId())).isEqualTo(reason);
        assertThat(service.latest(projectId).message()).isEqualTo(reason);
        assertThat(service.latest(projectId).status()).isEqualTo("FAILED");
    }

    private <T> T get(Future<T> future) {
        try { return future.get(15, TimeUnit.SECONDS); }
        catch (Exception e) { throw new AssertionError(e); }
    }
}
