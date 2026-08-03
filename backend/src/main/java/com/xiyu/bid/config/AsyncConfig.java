// Input: Spring 异步执行策略配置
// Output: 各业务专用线程池 Bean（均挂载 MdcTaskDecorator 传递 MDC 上下文）
// Pos: Config/基础设施层
package com.xiyu.bid.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步配置。
 * <p>为审计日志、AI 分析、人员导入导出、仓库导出、标讯导入提供专用线程池，
 * 避免阻塞主业务线程。
 *
 * <p><b>MDC 透传</b>：自 spec 031 起，所有 executor 均挂载 {@link MdcTaskDecorator}，
 * 将主线程的 traceId/userId/roleCode 复制到异步线程，确保异步任务内日志可追溯用户身份（CO-373/US3）。
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * 创建带 MdcTaskDecorator 的线程池（统一构造，避免重复代码）。
     */
    private ThreadPoolTaskExecutor createExecutor(String prefix, int core, int max, int queue) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(core);
        executor.setMaxPoolSize(max);
        executor.setQueueCapacity(queue);
        executor.setThreadNamePrefix(prefix);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setTaskDecorator(new MdcTaskDecorator());
        executor.initialize();
        return executor;
    }

    /** AI 分析专用线程池 */
    @Bean(name = "aiTaskExecutor")
    public Executor aiTaskExecutor() {
        return createExecutor("ai-async-", 2, 4, 100);
    }

    /** 操作日志专用线程池 */
    @Bean(name = "auditLogExecutor")
    public Executor auditLogExecutor() {
        return createExecutor("audit-log-", 2, 5, 100);
    }

    /** 人员证书导入导出专用线程池 */
    @Bean(name = "importExportExecutor")
    public Executor importExportExecutor() {
        return createExecutor("personnel-imp-exp-", 2, 4, 50);
    }

    /** 仓库台账导出专用线程池 */
    @Bean(name = "warehouseExportExecutor")
    public Executor warehouseExportExecutor() {
        return createExecutor("warehouse-export-", 2, 4, 20);
    }

    /**
     * 标讯批量导入专用线程池（spec 031 新增）。
     * <p>core=2, max=4, queue=50, CallerRunsPolicy：队列满时由调用线程执行，
     * 避免任务被拒绝导致用户看到"导入失败"。
     */
    @Bean(name = "tenderImportExecutor")
    public Executor tenderImportExecutor() {
        return createExecutor("tender-import-", 2, 4, 50);
    }

    /**
     * 业绩合订本导出专用线程池。
     * <p>core=1, max=2, queue=10：业绩合订本导出涉及 300 DPI 高清渲染，
     * 内存与 CPU 消耗较大，严格限制并发避免 OOM。
     * CallerRunsPolicy：队列满时由调用线程执行，避免任务被拒绝。
     */
    @Bean(name = "performanceBundleExportExecutor")
    public Executor performanceBundleExportExecutor() {
        return createExecutor("perf-bundle-export-", 1, 2, 10);
    }
}
