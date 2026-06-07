// Input: Spring 配置属性、环境变量、外部 bean 依赖
// Output: 配置 Bean、过滤器、线程池和启动级常量
// Pos: Config/基础设施层
// 维护声明: 仅维护配置与启动约束；业务规则变更请同步到对应 service/controller.
package com.xiyu.bid.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Export configuration properties
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.export")
public class ExportConfig {

    /**
     * Maximum number of records allowed in a single export
     */
    private int maxRecords = 10000;

    /**
     * Maximum file size in bytes (default: 50MB)
     */
    private long maxFileSizeBytes = 52_428_800L;

    /**
     * Whether to enable audit logging for exports
     */
    private boolean auditEnabled = true;

    /**
     * Rate limit: maximum exports per user per hour
     */
    private int maxExportsPerHour = 10;

    /**
     * Query timeout in seconds for export operations
     * Prevents long-running queries from blocking system resources
     */
    private int queryTimeoutSeconds = 60;
}
