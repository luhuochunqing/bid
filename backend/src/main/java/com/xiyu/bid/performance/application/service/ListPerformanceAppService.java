package com.xiyu.bid.performance.application.service;

import com.xiyu.bid.common.domain.PagedResult;
import com.xiyu.bid.performance.application.command.PerformanceSearchCriteria;
import com.xiyu.bid.performance.application.dto.PerformanceDTO;
import com.xiyu.bid.performance.application.mapper.PerformanceMapper;
import com.xiyu.bid.performance.domain.model.PerformanceRecord;
import com.xiyu.bid.performance.domain.port.PerformanceRepository;
import com.xiyu.bid.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ListPerformanceAppService {

    private final PerformanceRepository repository;
    private final PerformanceMapper mapper;
    private final PerformanceAlertConfigAppService configService;

    @Transactional(readOnly = true)
    public List<PerformanceDTO> list(PerformanceSearchCriteria criteria) {
        var config = configService.getConfig();
        return repository.findAll(criteria, config).stream().map(mapper::toDTO).toList();
    }

    @Transactional(readOnly = true)
    public PagedResult<PerformanceDTO> listPageable(PerformanceSearchCriteria criteria, int pageNumber, int pageSize) {
        var config = configService.getConfig();
        var page = repository.findAllPageable(criteria, config, pageNumber, pageSize);
        List<PerformanceDTO> dtos = page.content().stream().map(mapper::toDTO).toList();
        return new PagedResult<>(dtos, page.totalElements(), page.totalPages(), page.pageNumber(), page.pageSize(), page.hasNext(), page.hasPrevious());
    }

    @Transactional(readOnly = true)
    public PerformanceDTO get(Long id) {
        PerformanceRecord r = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PerformanceRecord", String.valueOf(id)));
        return mapper.toDTO(r);
    }
}
