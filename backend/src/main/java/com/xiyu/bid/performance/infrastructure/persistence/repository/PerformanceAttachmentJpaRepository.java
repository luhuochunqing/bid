package com.xiyu.bid.performance.infrastructure.persistence.repository;

import com.xiyu.bid.performance.infrastructure.persistence.entity.PerformanceAttachmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PerformanceAttachmentJpaRepository extends JpaRepository<PerformanceAttachmentEntity, Long> {

    List<PerformanceAttachmentEntity> findByPerformanceId(Long performanceId);

    void deleteByPerformanceId(Long performanceId);
}
