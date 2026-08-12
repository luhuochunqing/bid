package com.xiyu.bid.integration.tenderevent.infrastructure.persistence.repository;

import com.xiyu.bid.integration.tenderevent.infrastructure.persistence.entity.TenderEventLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 标讯事件推送流水仓库。
 */
public interface TenderEventLogRepository extends JpaRepository<TenderEventLogEntity, Long> {

    /**
     * 按标讯 ID 查询推送流水（用于问题定位）。
     */
    List<TenderEventLogEntity> findByTenderIdOrderByIdDesc(Long tenderId);
}