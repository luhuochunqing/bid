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
                command.totalExpiryDate(),
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
