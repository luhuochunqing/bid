package com.xiyu.bid.performance.application.service;

import com.xiyu.bid.performance.application.command.PerformanceUpsertCommand;
import com.xiyu.bid.performance.application.dto.PerformanceDTO;
import com.xiyu.bid.performance.application.mapper.PerformanceMapper;
import com.xiyu.bid.performance.domain.model.PerformanceRecord;
import com.xiyu.bid.performance.domain.port.PerformanceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CreatePerformanceAppService {

    private final PerformanceRepository repository;
    private final PerformanceMapper mapper;

    @Transactional
    public PerformanceDTO create(PerformanceUpsertCommand command) {
        PerformanceRecord record = mapper.toRecord(command);
        com.xiyu.bid.performance.domain.service.PerformanceValidator.validate(record)
                .ifPresent(error -> { throw new IllegalArgumentException(error); });
        return mapper.toDTO(repository.save(record));
    }
}
