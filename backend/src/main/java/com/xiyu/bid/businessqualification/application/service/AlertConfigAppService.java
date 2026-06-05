package com.xiyu.bid.businessqualification.application.service;

import com.xiyu.bid.businessqualification.domain.model.AlertConfig;
import com.xiyu.bid.businessqualification.domain.port.AlertConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 资质到期提醒告警配置的应用服务。
 * 职责：编排 AlertConfigRepository 进行配置的查询与更新，不包含业务规则计算。
 */
@Service
@RequiredArgsConstructor
public class AlertConfigAppService {

    private final AlertConfigRepository alertConfigRepository;

    @Transactional(readOnly = true)
    public AlertConfig getConfig() {
        return alertConfigRepository.findActive()
                .orElseGet(() -> new AlertConfig(null, 90, true));
    }

    @Transactional
    public AlertConfig updateConfig(int alertDays, boolean enabled) {
        AlertConfig existing = getConfig();
        AlertConfig updated;
        if (existing.id() != null) {
            updated = new AlertConfig(existing.id(), alertDays, enabled);
        } else {
            updated = new AlertConfig(null, alertDays, enabled);
        }
        return alertConfigRepository.save(updated);
    }
}
