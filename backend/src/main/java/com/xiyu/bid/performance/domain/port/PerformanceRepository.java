package com.xiyu.bid.performance.domain.port;

import com.xiyu.bid.common.domain.PagedResult;
import com.xiyu.bid.performance.application.command.PerformanceSearchCriteria;
import com.xiyu.bid.performance.domain.model.PerformanceAlertConfig;
import com.xiyu.bid.performance.domain.model.PerformanceRecord;

import java.util.List;
import java.util.Optional;

public interface PerformanceRepository {

    PerformanceRecord save(PerformanceRecord record);

    Optional<PerformanceRecord> findById(Long id);

    Optional<PerformanceRecord> findByContractName(String contractName);

    List<PerformanceRecord> findAll(PerformanceSearchCriteria criteria, PerformanceAlertConfig config);

    PagedResult<PerformanceRecord> findAllPageable(PerformanceSearchCriteria criteria, PerformanceAlertConfig config, int pageNumber, int pageSize);

    void deleteById(Long id);

    long count();

    /**
     * 查询所有有到期日期的业绩记录（供到期提醒扫描使用，不过滤日期范围）。
     * 由 {@link com.xiyu.bid.performance.application.service.ScanExpiringPerformanceAppService} 调用，
     * 由 {@link com.xiyu.bid.performance.infrastructure.persistence.PerformanceRepositoryAdapter} 实现。
     */
    List<PerformanceRecord> findAllWithExpiryDate();
}
