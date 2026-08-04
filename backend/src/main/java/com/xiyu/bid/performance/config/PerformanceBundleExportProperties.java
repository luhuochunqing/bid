package com.xiyu.bid.performance.config;

import com.xiyu.bid.common.util.PathUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.Duration;

/**
 * 业绩合订本导出配置。
 *
 * <p>统一管理 exportRoot / fileTtl / maxExportRecords 等配置项，
 * 避免在多个 Bean 中重复 {@code @Value} 注入导致的"约定非约束"脆弱设计。
 *
 * <p>对应配置前缀：{@code performance.bundle-export}
 *
 * @since CO-602 PR 设计评估修复（D2-1）
 */
@Component
@ConfigurationProperties(prefix = "performance.bundle-export")
public class PerformanceBundleExportProperties {

    /** maxExportRecords 默认值（供 Bean Validation 注解等需要编译期常量的场景引用）。 */
    public static final int DEFAULT_MAX_EXPORT_RECORDS = 2000;

    /**
     * 导出文件落盘根目录（与 AsyncExecutor / AppService 共用同一配置源）。
     */
    private String root = "data/performance-bundle-exports";

    /**
     * 导出文件保留时长。超期后由清理任务删除并标记任务失败。
     * <p>默认 7 天，覆盖周末场景（D5-1 修复：原 24h 过短）。
     */
    private Duration fileTtl = Duration.ofDays(7);

    /**
     * 单次导出记录数上限。超过则拒绝导出（D4-1 修复：原 5000 过高，OOM 风险）。
     */
    private int maxExportRecords = DEFAULT_MAX_EXPORT_RECORDS;

    public String getRoot() { return root; }
    public void setRoot(String root) {
        if (root == null || root.isBlank()) return;
        this.root = root;
    }

    public Duration getFileTtl() { return fileTtl; }
    public void setFileTtl(Duration fileTtl) {
        if (fileTtl != null) this.fileTtl = fileTtl;
    }

    public int getMaxExportRecords() { return maxExportRecords; }
    public void setMaxExportRecords(int maxExportRecords) {
        if (maxExportRecords > 0) this.maxExportRecords = maxExportRecords;
    }

    /**
     * 归一化为绝对路径（统一入口，避免在两个 Bean 中各自实现）。
     */
    public Path resolveAbsoluteRoot() {
        return PathUtils.resolveAbsolute(root);
    }
}
