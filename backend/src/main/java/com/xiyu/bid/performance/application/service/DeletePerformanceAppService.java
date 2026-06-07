package com.xiyu.bid.performance.application.service;

import com.xiyu.bid.performance.domain.port.PerformanceRepository;
import com.xiyu.bid.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DeletePerformanceAppService {

    private final PerformanceRepository repository;

    @Transactional
    public void delete(Long id) {
        if (repository.findById(id).isEmpty()) {
            throw new ResourceNotFoundException("PerformanceRecord", String.valueOf(id));
        }
        repository.deleteById(id);
    }
}
