package com.xiyu.bid.businessqualification.infrastructure.persistence;

import com.xiyu.bid.businessqualification.domain.model.AlertConfig;
import com.xiyu.bid.businessqualification.domain.port.AlertConfigRepository;
import com.xiyu.bid.businessqualification.infrastructure.persistence.entity.AlertConfigEntity;
import com.xiyu.bid.businessqualification.infrastructure.persistence.repository.AlertConfigJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * AlertConfigRepository 的 JPA 适配器。
 * 职责仅限于数据访问与实体-领域对象转换，不含业务规则。
 */
@Component
@RequiredArgsConstructor
public class AlertConfigRepositoryAdapter implements AlertConfigRepository {

    private final AlertConfigJpaRepository jpaRepository;

    @Override
    public Optional<AlertConfig> findActive() {
        // 约定：只维护一条生效配置，取第一条记录
        return jpaRepository.findAll().stream()
                .findFirst()
                .map(AlertConfigEntity::toDomain);
    }

    @Override
    public AlertConfig save(AlertConfig config) {
        AlertConfigEntity entity = AlertConfigEntity.fromDomain(config);
        AlertConfigEntity saved = jpaRepository.save(entity);
        return saved.toDomain();
    }
}
