package com.xiyu.bid.platform.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import com.xiyu.bid.entity.User;
import com.xiyu.bid.infrastructure.excel.SingleSheetExcelReader;
import com.xiyu.bid.infrastructure.excel.SingleSheetExcelReader.WorkbookData;
import com.xiyu.bid.platform.domain.PlatformAccountImportPolicy;
import com.xiyu.bid.platform.infrastructure.persistence.entity.PlatformAccountImportTaskEntity;
import com.xiyu.bid.platform.infrastructure.persistence.repository.PlatformAccountImportTaskJpaRepository;
import com.xiyu.bid.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

/**
 * PlatformAccountImportAsyncRunner @Async 代理集成测试（CO-560 补强）。
 *
 * <p><b>验证目标</b>：@Async 注解通过 Spring AOP 代理实际生效，异步方法在独立线程执行。
 *
 * <p><b>背景</b>：单元测试通过 {@code new AsyncRunner(...)} 直接构造对象，完全绕过了
 * Spring 代理，无法发现 @Async 自调用失效类问题。本测试用 @SpringBootTest 让 Spring
 * 创建代理 bean，验证调用线程 ≠ 执行线程。
 *
 * <p><b>原理</b>：@Async 代理在方法调用时会提交任务到 {@code tenderImportExecutor} 线程池，
 * 主线程立即返回。通过 CountDownLatch 同步，在 rowPersister.persist mock 内记录执行线程 ID，
 * 主线程 await 后断言线程 ID 不同。
 */
@SpringBootTest(properties = "spring.main.allow-bean-definition-overriding=true")
@ActiveProfiles("test")
@DisplayName("PlatformAccountImportAsyncRunner @Async 代理集成测试")
class PlatformAccountImportAsyncRunnerAsyncProxyIntegrationTest {

    @Autowired
    private PlatformAccountImportAsyncRunner runner;

    @MockBean
    private SingleSheetExcelReader excelReader;
    @MockBean
    private PlatformAccountImportRowPersister rowPersister;
    @MockBean
    private PlatformAccountImportTaskJpaRepository taskRepo;
    @MockBean
    private UserRepository userRepository;

    private static final Long TASK_ID = 9001L;
    private static final Long OPERATOR_ID = 100L;
    private static final String VALID_EMPLOYEE_NUMBER = "EMP001";
    private static final Long CUSTODIAN_USER_ID = 42L;

    @Test
    @DisplayName("@Async 代理生效：调用线程 ≠ 执行线程")
    void asyncProxy_executesOnDifferentThread() throws Exception {
        // 准备 mock
        PlatformAccountImportTaskEntity task = new PlatformAccountImportTaskEntity();
        task.setId(TASK_ID);
        when(taskRepo.findById(TASK_ID)).thenReturn(Optional.of(task));
        when(taskRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        String[] header = PlatformAccountImportPolicy.HEADERS.clone();
        String[] row = {"测试平台", "https://example.com", "admin", "pass123",
                VALID_EMPLOYEE_NUMBER, "否", "", "", "", ""};
        when(excelReader.read(any(byte[].class))).thenReturn(new WorkbookData(List.of(header, row)));

        User custodian = User.builder().id(CUSTODIAN_USER_ID).employeeNumber(VALID_EMPLOYEE_NUMBER).build();
        when(userRepository.findByEmployeeNumber(VALID_EMPLOYEE_NUMBER)).thenReturn(Optional.of(custodian));

        // CountDownLatch + AtomicLong：在 persist mock 内记录执行线程 ID
        CountDownLatch persistLatch = new CountDownLatch(1);
        AtomicLong asyncThreadId = new AtomicLong(-1L);
        doAnswer(inv -> {
            asyncThreadId.set(Thread.currentThread().getId());
            persistLatch.countDown();
            return null;
        }).when(rowPersister).persist(any(), any());

        // 记录调用线程 ID
        long callerThreadId = Thread.currentThread().getId();

        // 调用异步方法（通过 Spring 代理）
        runner.executeImportAsync(TASK_ID, new byte[0], OPERATOR_ID);

        // 等待异步任务执行 persist（5 秒超时）
        boolean completed = persistLatch.await(5, TimeUnit.SECONDS);

        // 断言：异步任务确实执行了
        assertThat(completed)
                .as("异步任务应在 5 秒内执行 persist（@Async 代理应让方法在独立线程执行）")
                .isTrue();

        // 核心断言：调用线程 ≠ 执行线程
        assertThat(asyncThreadId.get())
                .as("@Async 代理生效：persist 应在异步线程执行，而非调用线程")
                .isNotEqualTo(callerThreadId);

        // 验证线程名（tenderImportExecutor 的线程名前缀是 "tender-import-"）
        String asyncThreadName = Thread.getAllStackTraces().keySet().stream()
                .filter(t -> t.getId() == asyncThreadId.get())
                .map(Thread::getName)
                .findFirst()
                .orElse("(线程已退出)");
        assertThat(asyncThreadName)
                .as("异步线程应来自 tenderImportExecutor 线程池")
                .startsWith("tender-import-");
    }

    @Test
    @DisplayName("@Async 代理生效：调用方法后主线程立即返回（不等异步完成）")
    void asyncProxy_callerReturnsImmediately() throws Exception {
        // 准备 mock
        PlatformAccountImportTaskEntity task = new PlatformAccountImportTaskEntity();
        task.setId(TASK_ID);
        when(taskRepo.findById(TASK_ID)).thenReturn(Optional.of(task));
        when(taskRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // 让 excelReader.read 阻塞 2 秒，模拟耗时操作
        CountDownLatch readStarted = new CountDownLatch(1);
        when(excelReader.read(any(byte[].class))).thenAnswer(inv -> {
            readStarted.countDown();
            Thread.sleep(2000);
            String[] header = PlatformAccountImportPolicy.HEADERS.clone();
            return new WorkbookData(java.util.Collections.singletonList(header));
        });

        long startNs = System.nanoTime();
        // 调用异步方法
        runner.executeImportAsync(TASK_ID, new byte[0], OPERATOR_ID);
        long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;

        // 核心断言：主线程应在 1 秒内返回（不等 excelReader.read 的 2 秒 sleep）
        assertThat(elapsedMs)
                .as("@Async 代理生效：主线程应立即返回，不应阻塞等待 excelReader.read 的 2 秒 sleep")
                .isLessThan(1000L);

        // 验证异步任务确实启动了
        assertThat(readStarted.await(5, TimeUnit.SECONDS))
                .as("异步任务应在 5 秒内启动 excelReader.read")
                .isTrue();
    }
}
