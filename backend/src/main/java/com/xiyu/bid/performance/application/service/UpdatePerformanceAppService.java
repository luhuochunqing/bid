package com.xiyu.bid.performance.application.service;

import com.xiyu.bid.performance.application.command.PerformanceUpsertCommand;
import com.xiyu.bid.performance.application.dto.PerformanceDTO;
import com.xiyu.bid.performance.application.mapper.PerformanceMapper;
import com.xiyu.bid.performance.domain.model.PerformanceRecord;
import com.xiyu.bid.performance.domain.port.PerformanceRepository;
import com.xiyu.bid.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UpdatePerformanceAppService {

    private final PerformanceRepository repository;
    private final PerformanceMapper mapper;

    @Transactional
    public PerformanceDTO update(Long id, PerformanceUpsertCommand command) {
        PerformanceRecord existing = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PerformanceRecord", String.valueOf(id)));

        PerformanceRecord updated = new PerformanceRecord(
                existing.id(),
                command.contractName(),
                command.signingEntity(),
                command.groupCompany(),
                command.customerType(),
                command.industry(),
                command.projectType(),
                command.dockingMethod(),
                command.customerLevel(),
                command.signingDate(),
                command.expiryDate(),
                // CO-583: totalExpiryDate 已下线用户输入，这里传 null 表示"不更新此字段"。
                // 隐式契约：PerformanceRepositoryAdapter.updateEntityFields 见到 null 跳过 setTotalExpiryDate，
                // 从而保留实体原值（历史数据）。改动 Adapter.updateEntityFields 或本方法时需同步检查此契约。
                // 防御性提示：若未来 Adapter 改为 "null 也写入"，此处历史值会丢失，需改为 existing.totalExpiryDate()。
                null,
                command.contactPerson(),
                command.contactInfo(),
                command.territory(),
                command.customerAddress(),
                command.xiyuProjectManager(),
                command.mallWebsiteUrl(),
                command.hasBidNotice(),
                command.remarks(),
                mapper.toAttachmentEntries(command.attachments()),
                existing.createdAt(),
                java.time.LocalDateTime.now()
        );
        com.xiyu.bid.performance.domain.service.PerformanceValidator.validate(updated)
                .ifPresent(error -> { throw new IllegalArgumentException(error); });
        return mapper.toDTO(repository.save(updated));
    }
}
