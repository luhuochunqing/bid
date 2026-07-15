package com.xiyu.bid.performance.domain.port;

import com.xiyu.bid.common.domain.PagedResult;
import com.xiyu.bid.performance.application.command.PerformanceSearchCriteria;
import com.xiyu.bid.performance.domain.model.PerformanceAlertConfig;
import com.xiyu.bid.performance.domain.model.PerformanceRecord;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
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

    /**
     * CO-583: 查询集团 → MAX(expiryDate) 聚合映射（基于全量数据，不受筛选条件影响）。
     * 用于列表/导出统一展示"总截止日期"。
     * 仅返回 groupCompany 非空且 expiryDate 非空的记录聚合结果。
     */
    Map<String, LocalDate> findGroupTotalExpiryDates();

    /**
     * CO-583: 查询指定集团的 MAX(expiryDate)（详情页单值查询，避免全表聚合）。
     * @param groupCompany 集团名称，null 或空返回 Optional.empty()
     * @return 该集团内 expiryDate 的最大值；集团无有效记录时返回 Optional.empty()
     */
    Optional<LocalDate> findGroupTotalExpiryDate(String groupCompany);
}
