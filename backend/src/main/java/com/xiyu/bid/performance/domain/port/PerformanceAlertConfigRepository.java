package com.xiyu.bid.performance.domain.port;

import com.xiyu.bid.performance.domain.model.PerformanceAlertConfig;

import java.util.Optional;

/**
 * 业绩到期提醒配置仓储端口。
 */
public interface PerformanceAlertConfigRepository {

    Optional<PerformanceAlertConfig> findActive();

    PerformanceAlertConfig save(PerformanceAlertConfig config);
}
