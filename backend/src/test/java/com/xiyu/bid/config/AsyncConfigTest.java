package com.xiyu.bid.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * AsyncConfig 单元测试。
 *
 * <p>防复发目标（P1-4）：
 * <ul>
 *   <li>验证 {@code performanceBundleExportExecutor} 使用 {@link ThreadPoolExecutor.AbortPolicy}，
 *       而非 CallerRunsPolicy。避免 HTTP 线程被阻塞执行 5-10 分钟的 Word 渲染导致网关超时。</li>
 * </ul>
 */
class AsyncConfigTest {

    private final AsyncConfig asyncConfig = new AsyncConfig();

    @Test
    @DisplayName("performanceBundleExportExecutor 必须使用 AbortPolicy（P1-4 防复发）")
    void performanceBundleExportExecutor_shouldUseAbortPolicy() {
        Executor executor = asyncConfig.performanceBundleExportExecutor();
        assertInstanceOf(ThreadPoolTaskExecutor.class, executor);

        ThreadPoolTaskExecutor poolExecutor = (ThreadPoolTaskExecutor) executor;
        // 队列满时必须抛 RejectedExecutionException，由 Controller 捕获返回 503
        assertInstanceOf(ThreadPoolExecutor.AbortPolicy.class,
                poolExecutor.getThreadPoolExecutor().getRejectedExecutionHandler(),
                "performanceBundleExportExecutor 必须使用 AbortPolicy，避免阻塞 HTTP 线程");
    }

    @Test
    @DisplayName("其他 executor 保留 CallerRunsPolicy（不回归）")
    void otherExecutors_shouldKeepCallerRunsPolicy() {
        // 抽查一个其他 executor，确保 createExecutor 默认仍是 CallerRunsPolicy
        Executor executor = asyncConfig.auditLogExecutor();
        assertInstanceOf(ThreadPoolTaskExecutor.class, executor);

        ThreadPoolTaskExecutor poolExecutor = (ThreadPoolTaskExecutor) executor;
        assertInstanceOf(ThreadPoolExecutor.CallerRunsPolicy.class,
                poolExecutor.getThreadPoolExecutor().getRejectedExecutionHandler(),
                "非业绩导出 executor 应保留 CallerRunsPolicy");
    }

    @Test
    @DisplayName("performanceBundleExportExecutor 队列满时抛 RejectedExecutionException")
    void performanceBundleExportExecutor_queueFull_throwsRejectedExecution() {
        Executor executor = asyncConfig.performanceBundleExportExecutor();
        ThreadPoolTaskExecutor poolExecutor = (ThreadPoolTaskExecutor) executor;

        // core=1, max=2, queue=10：填满 12 个任务后第 13 个应被拒绝
        Runnable blockingTask = () -> {
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };

        // 提交 12 个任务填满线程池 + 队列
        for (int i = 0; i < 12; i++) {
            executor.execute(blockingTask);
        }

        // 第 13 个任务应触发 AbortPolicy
        assertThrows(RejectedExecutionException.class,
                () -> executor.execute(blockingTask),
                "队列满后必须抛 RejectedExecutionException，由 Controller 捕获返回 503");

        // 清理：关闭线程池
        poolExecutor.shutdown();
    }
}
