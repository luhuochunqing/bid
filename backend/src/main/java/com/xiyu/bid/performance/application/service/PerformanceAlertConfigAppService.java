package com.xiyu.bid.performance.application.service;

import com.xiyu.bid.performance.domain.model.PerformanceAlertConfig;
import com.xiyu.bid.performance.domain.port.PerformanceAlertConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 业绩到期提醒配置应用服务。
 * 职责：编排 PerformanceAlertConfigRepository 进行配置的查询与更新。
 */
@Service
@RequiredArgsConstructor
public class PerformanceAlertConfigAppService {

    private final PerformanceAlertConfigRepository repository;

    /**
     * 获取当前配置。若无配置，返回默认值（央企 180 天 / 其他 90 天，启用）。
     */
    @Transactional(readOnly = true)
    public PerformanceAlertConfig getConfig() {
        return repository.findActive()
                .orElseGet(() -> new PerformanceAlertConfig(null, 180, 90, true));
    }

    /**
     * 更新提醒配置。若已存在记录则更新 id，否则插入新行。
     */
    @Transactional
    public PerformanceAlertConfig updateConfig(int alertDaysSoe, int alertDaysDefault, boolean enabled) {
        PerformanceAlertConfig updated = repository.findActive()
                .map(existing -> new PerformanceAlertConfig(existing.id(), alertDaysSoe, alertDaysDefault, enabled))
                .orElseGet(() -> new PerformanceAlertConfig(null, alertDaysSoe, alertDaysDefault, enabled));
        return repository.save(updated);
    }
}
