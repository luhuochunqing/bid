package com.xiyu.bid.performance.infrastructure.persistence;

import com.xiyu.bid.performance.domain.model.PerformanceAlertConfig;
import com.xiyu.bid.performance.domain.port.PerformanceAlertConfigRepository;
import com.xiyu.bid.performance.infrastructure.persistence.entity.PerformanceAlertConfigEntity;
import com.xiyu.bid.performance.infrastructure.persistence.repository.PerformanceAlertConfigJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 业绩到期提醒配置仓储适配器。
 * 实现 {@link PerformanceAlertConfigRepository} 端口，将领域模型映射到 JPA 实体。
 */
@Component
@RequiredArgsConstructor
public class PerformanceAlertConfigRepositoryAdapter implements PerformanceAlertConfigRepository {

    private final PerformanceAlertConfigJpaRepository jpaRepository;

    @Override
    public Optional<PerformanceAlertConfig> findActive() {
        return jpaRepository.findAll().stream()
                .findFirst()
                .map(PerformanceAlertConfigEntity::toDomain);
    }

    @Override
    public PerformanceAlertConfig save(PerformanceAlertConfig config) {
        PerformanceAlertConfigEntity entity = PerformanceAlertConfigEntity.fromDomain(config);
        PerformanceAlertConfigEntity saved = jpaRepository.save(entity);
        return saved.toDomain();
    }
}
