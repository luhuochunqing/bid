package com.xiyu.bid.performance.infrastructure.persistence.repository;

import com.xiyu.bid.performance.infrastructure.persistence.entity.PerformanceAttachmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface PerformanceAttachmentJpaRepository extends JpaRepository<PerformanceAttachmentEntity, Long> {

    List<PerformanceAttachmentEntity> findByPerformanceId(Long performanceId);

    /**
     * 按业绩 ID 批量查询附件（避免批量加载业绩时逐条查附件的 N+1）。
     */
    List<PerformanceAttachmentEntity> findByPerformanceIdIn(Collection<Long> performanceIds);

    void deleteByPerformanceId(Long performanceId);
}
