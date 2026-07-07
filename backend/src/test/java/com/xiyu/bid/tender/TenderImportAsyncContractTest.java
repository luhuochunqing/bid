package com.xiyu.bid.tender;

import com.xiyu.bid.config.AsyncConfig;
import com.xiyu.bid.config.MdcTaskDecorator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 标讯异步任务契约测试（T020）。
 *
 * <p>验证 @Async 关键契约（不启动 Spring 上下文，仅验证线程池 + MdcTaskDecorator 行为）：
 * <ol>
 *   <li>async 方法实际在新线程执行（非主线程）</li>
 *   <li>主线程 MDC 上下文（traceId/userId/roleCode）被复制到异步线程</li>
 *   <li>异步线程结束后 MDC 被清理（线程池复用不串味）</li>
 *   <li>tenderImportExecutor 使用 MdcTaskDecorator</li>
 * </ol>
 *
 * <p>契约覆盖 spec 031 US3（MDC 修复）+ US1（异步化）的底层保障。
 */
class TenderImportAsyncContractTest {

    @Test
    @DisplayName("MdcTaskDecorator 复制主线程 MDC 到异步线程，结束后清理")
    void mdcTaskDecorator_copiesMdcAndClearsAfterRun() throws Exception {
        // 主线程设置 MDC
        MDC.put("traceId", "trace-abc");
        MDC.put("userId", "user-100");
        MDC.put("roleCode", "BID_TEAMLEADER");

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setTaskDecorator(new MdcTaskDecorator());
        executor.initialize();

        AtomicReference<Map<String, String>> asyncMdc = new AtomicReference<>();
        AtomicReference<String> asyncThreadName = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        try {
            executor.submit(() -> {
                asyncMdc.set(MDC.getCopyOfContextMap());
                asyncThreadName.set(Thread.currentThread().getName());
                latch.countDown();
            });

            assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
            // 验证：异步线程拿到了主线程的 MDC
            Map<String, String> captured = asyncMdc.get();
            assertThat(captured).isNotNull();
            assertThat(captured).containsEntry("traceId", "trace-abc");
            assertThat(captured).containsEntry("userId", "user-100");
            assertThat(captured).containsEntry("roleCode", "BID_TEAMLEADER");
            // 验证：异步线程名不是主线程
            assertThat(asyncThreadName.get()).isNotEqualTo(Thread.currentThread().getName());
        } finally {
            MDC.clear();
            executor.shutdown();
        }
    }

    @Test
    @DisplayName("MdcTaskDecorator 在任务结束后 clear MDC（线程池复用不串味）")
    void mdcTaskDecorator_clearsMdcAfterTaskCompletes() throws Exception {
        MDC.put("traceId", "trace-leak-test");
        MDC.put("userId", "user-leak");

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setTaskDecorator(new MdcTaskDecorator());
        executor.initialize();

        CountDownLatch firstTask = new CountDownLatch(1);
        CountDownLatch secondTask = new CountDownLatch(1);
        AtomicReference<Map<String, String>> secondTaskMdc = new AtomicReference<>();

        try {
            // 第一个任务：会拿到主线程的 MDC
            executor.submit(() -> {
                firstTask.countDown();
            });
            assertThat(firstTask.await(2, TimeUnit.SECONDS)).isTrue();

            // 清空主线程 MDC，模拟请求结束后 MDC 被清理
            MDC.clear();

            // 第二个任务（同一线程池）：不应看到第一个任务的 MDC
            executor.submit(() -> {
                secondTaskMdc.set(MDC.getCopyOfContextMap());
                secondTask.countDown();
            });
            assertThat(secondTask.await(2, TimeUnit.SECONDS)).isTrue();

            // 验证：第二个任务拿到的 MDC 为 null 或空（不串味）
            Map<String, String> captured = secondTaskMdc.get();
            assertThat(captured == null || captured.isEmpty())
                    .as("第二个任务不应看到第一个任务的 MDC（线程池复用清理）")
                    .isTrue();
        } finally {
            MDC.clear();
            executor.shutdown();
        }
    }

    @Test
    @DisplayName("AsyncConfig.tenderImportExecutor 挂载 MdcTaskDecorator（确保异步导入有 MDC）")
    void asyncConfig_tenderImportExecutor_hasMdcTaskDecorator() {
        AsyncConfig config = new AsyncConfig();
        org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor executor =
                (org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor) config.tenderImportExecutor();

        try {
            // 验证线程池配置
            assertThat(executor.getCorePoolSize()).isEqualTo(2);
            assertThat(executor.getMaxPoolSize()).isEqualTo(4);
            assertThat(executor.getThreadNamePrefix()).isEqualTo("tender-import-");

            // 验证 MDC 传递契约：主线程 MDC 应能在异步线程读到
            MDC.put("traceId", "contract-test");
            MDC.put("userId", "user-200");
            MDC.put("roleCode", "ADMIN");

            AtomicReference<Map<String, String>> asyncMdc = new AtomicReference<>();
            CountDownLatch latch = new CountDownLatch(1);

            executor.submit(() -> {
                asyncMdc.set(MDC.getCopyOfContextMap());
                latch.countDown();
            });

            assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
            Map<String, String> captured = asyncMdc.get();
            assertThat(captured).isNotNull();
            assertThat(captured).containsEntry("traceId", "contract-test");
            assertThat(captured).containsEntry("userId", "user-200");
            assertThat(captured).containsEntry("roleCode", "ADMIN");
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            MDC.clear();
            executor.shutdown();
        }
    }

    @Test
    @DisplayName("@Async 契约：async 方法在新线程执行，非调用线程")
    void asyncMethod_runsInDifferentThread() throws Exception {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setTaskDecorator(new MdcTaskDecorator());
        executor.initialize();

        AsyncChecker checker = new AsyncChecker();
        String mainThreadName = Thread.currentThread().getName();

        try {
            checker.checkAsync(executor, mainThreadName);
            // 等待异步线程完成（给 2s 超时）
            AtomicReference<String> asyncThread = checker.asyncThread;
            long start = System.currentTimeMillis();
            while (asyncThread.get() == null && System.currentTimeMillis() - start < 2000) {
                Thread.sleep(10);
            }

            assertThat(asyncThread.get())
                    .as("@Async 方法应在新线程执行")
                    .isNotNull()
                    .isNotEqualTo(mainThreadName);
        } finally {
            executor.shutdown();
        }
    }

    /**
     * 辅助组件：模拟 @Async 方法的线程切换行为。
     */
    static class AsyncChecker {
        final AtomicReference<String> asyncThread = new AtomicReference<>();

        void checkAsync(ThreadPoolTaskExecutor executor, String mainThreadName) {
            executor.submit(() -> {
                asyncThread.set(Thread.currentThread().getName());
            });
        }
    }
}
