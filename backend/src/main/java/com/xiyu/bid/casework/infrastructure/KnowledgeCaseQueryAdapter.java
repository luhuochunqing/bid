package com.xiyu.bid.casework.infrastructure;

import com.xiyu.bid.casework.application.port.KnowledgeCaseQueryPort;
import com.xiyu.bid.casework.domain.model.KnowledgeCaseReadModel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * KnowledgeCaseQueryPort 的 JPA 适配器。
 *
 * <p>封装 KnowledgeCaseRepository 访问并将 ACTIVE 过滤逻辑下沉到基础设施层，
 * 使 application 层保持纯编排。
 */
@Component
@RequiredArgsConstructor
public class KnowledgeCaseQueryAdapter implements KnowledgeCaseQueryPort {

    private final KnowledgeCaseRepository knowledgeCaseRepository;

    @Override
    public List<KnowledgeCaseReadModel> findAllActive() {
        return knowledgeCaseRepository.findAll().stream()
                .filter(c -> "ACTIVE".equalsIgnoreCase(c.getStatus()) || c.getStatus() == null)
                .map(c -> (KnowledgeCaseReadModel) c)
                .toList();
    }
}
