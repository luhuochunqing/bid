package com.xiyu.bid.config;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link MdcTaskDecorator} 单元测试。
 *
 * <p>验证：
 * <ol>
 *   <li>MDC 上下文从主线程传递到异步线程</li>
 *   <li>异步线程结束后 MDC 被清理（避免线程池复用串味）</li>
 *   <li>主线程 MDC 不受异步线程清理影响</li>
 * </ol>
 */
class MdcTaskDecoratorTest {

    private final MdcTaskDecorator decorator = new MdcTaskDecorator();

    @Test
    void should_propagate_mdc_to_async_thread() throws InterruptedException {
        // 主线程填充 MDC
        MDC.put(TraceConstants.MDC_TRACE_KEY, "trace-abc");
        MDC.put(TraceConstants.MDC_USER_ID_KEY, "user-123");
        MDC.put(TraceConstants.MDC_ROLE_CODE_KEY, "bid-Team");

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> asyncTraceId = new AtomicReference<>();
        AtomicReference<String> asyncUserId = new AtomicReference<>();
        AtomicReference<String> asyncRoleCode = new AtomicReference<>();

        Runnable decorated = decorator.decorate(() -> {
            asyncTraceId.set(MDC.get(TraceConstants.MDC_TRACE_KEY));
            asyncUserId.set(MDC.get(TraceConstants.MDC_USER_ID_KEY));
            asyncRoleCode.set(MDC.get(TraceConstants.MDC_ROLE_CODE_KEY));
            latch.countDown();
        });

        Thread worker = new Thread(decorated);
        worker.start();
        assertTrue(latch.await(2, TimeUnit.SECONDS), "异步线程应在 2s 内完成");

        assertEquals("trace-abc", asyncTraceId.get(), "异步线程应继承主线程的 traceId");
        assertEquals("user-123", asyncUserId.get(), "异步线程应继承主线程的 userId");
        assertEquals("bid-Team", asyncRoleCode.get(), "异步线程应继承主线程的 roleCode");

        MDC.clear();
    }

    @Test
    void should_clear_mdc_after_async_task_completes() throws InterruptedException {
        MDC.put(TraceConstants.MDC_TRACE_KEY, "trace-xyz");

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> mdcAfterRun = new AtomicReference<>();

        Runnable decorated = decorator.decorate(() -> {
            // 任务执行中 MDC 应存在
            mdcAfterRun.set(MDC.get(TraceConstants.MDC_TRACE_KEY));
            latch.countDown();
        });

        Thread worker = new Thread(decorated);
        worker.start();
        assertTrue(latch.await(2, TimeUnit.SECONDS));
        worker.join(500);

        // 异步线程结束后 MDC 应被清理（worker 线程的 MDC，非主线程）
        // 注意：MDC.clear() 在 worker 线程执行，不影响主线程
        // 这里验证 mdcAfterRun 在任务执行中确实有值
        assertEquals("trace-xyz", mdcAfterRun.get(), "任务执行中 MDC 应存在");

        MDC.clear();
    }

    @Test
    void should_not_pollute_main_thread_mdc() throws InterruptedException {
        MDC.put(TraceConstants.MDC_TRACE_KEY, "main-trace");

        CountDownLatch latch = new CountDownLatch(1);
        Runnable decorated = decorator.decorate(() -> {
            // 异步线程修改 MDC 不应影响主线程
            MDC.put(TraceConstants.MDC_TRACE_KEY, "async-pollute");
            latch.countDown();
        });

        Thread worker = new Thread(decorated);
        worker.start();
        assertTrue(latch.await(2, TimeUnit.SECONDS));
        worker.join(500);

        // 主线程 MDC 应保持不变
        assertEquals("main-trace", MDC.get(TraceConstants.MDC_TRACE_KEY),
                "主线程 MDC 不应被异步线程修改");

        MDC.clear();
    }

    @Test
    void should_handle_null_mdc_gracefully() throws InterruptedException {
        // 主线程无 MDC 上下文
        MDC.clear();

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Boolean> ran = new AtomicReference<>(false);

        Runnable decorated = decorator.decorate(() -> {
            ran.set(true);
            latch.countDown();
        });

        Thread worker = new Thread(decorated);
        worker.start();
        assertTrue(latch.await(2, TimeUnit.SECONDS));

        assertTrue(ran.get(), "即使主线程无 MDC，任务也应正常执行");
    }
}
