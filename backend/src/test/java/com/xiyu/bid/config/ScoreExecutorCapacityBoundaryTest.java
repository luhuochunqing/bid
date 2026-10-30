package com.xiyu.bid.config;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import static org.assertj.core.api.Assertions.assertThat;

/** Characterizes the existing shared pool; it does not claim saturation is safe for HTTP callers. */
class ScoreExecutorCapacityBoundaryTest {
    @Test void isolatedSubmissionRunsOffCallerAndExistingCapacityIsUnchanged() throws Exception {
        var executor = (ThreadPoolTaskExecutor) new AsyncConfig().scoreParseExecutor();
        var completed = new CountDownLatch(1);
        var thread = new AtomicReference<String>();
        String caller = Thread.currentThread().getName();
        try {
            assertThat(executor.getCorePoolSize()).isEqualTo(1);
            assertThat(executor.getMaxPoolSize()).isEqualTo(2);
            assertThat(executor.getThreadPoolExecutor().getQueue().remainingCapacity()).isEqualTo(20);
            assertThat(executor.getThreadPoolExecutor().getRejectedExecutionHandler())
                    .isInstanceOf(ThreadPoolExecutor.CallerRunsPolicy.class);
            executor.execute(() -> { thread.set(Thread.currentThread().getName()); completed.countDown(); });
            assertThat(completed.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(thread.get()).startsWith("score-parse-").isNotEqualTo(caller);
        } finally {
            executor.getThreadPoolExecutor().shutdownNow();
            assertThat(executor.getThreadPoolExecutor().awaitTermination(2, TimeUnit.SECONDS)).isTrue();
        }
    }
}
