package com.xiyu.bid.config;

import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

import java.util.Map;

/**
 * {@link TaskDecorator} 实现：将主线程的 MDC 上下文复制到异步线程，执行完后清理。
 *
 * <p>用途：{@code @Async} 线程不会自动继承 ThreadLocal，导致异步任务内的日志
 * 丢失 traceId/userId/roleCode。本类在任务提交时快照主线程 MDC，在异步线程
 * 启动时恢复，结束后 {@link MDC#clear()} 清理，避免线程池复用时 MDC 串味。
 *
 * <p>使用方式：在 {@code AsyncConfig} 里给每个 {@code ThreadPoolTaskExecutor}
 * 调用 {@code executor.setTaskDecorator(new MdcTaskDecorator())}。
 *
 * @see org.springframework.scheduling.annotation.Async
 * @see org.springframework.core.task.TaskDecorator
 */
public class MdcTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        // 在提交任务的主线程捕获 MDC 快照（可能为 null，如无 MDC 上下文）
        Map<String, String> contextMap = MDC.getCopyOfContextMap();
        return () -> {
            if (contextMap != null && !contextMap.isEmpty()) {
                MDC.setContextMap(contextMap);
            }
            try {
                runnable.run();
            } finally {
                // 线程池复用前清理，避免下一个任务读到上一个任务的 MDC
                MDC.clear();
            }
        };
    }
}
