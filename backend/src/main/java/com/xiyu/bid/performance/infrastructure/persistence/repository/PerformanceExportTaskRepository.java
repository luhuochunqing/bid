package com.xiyu.bid.performance.infrastructure.persistence.repository;

import com.xiyu.bid.performance.infrastructure.persistence.entity.PerformanceExportTaskEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 业绩合订本导出任务仓库。
 *
 * <p>查询范围受 createdBy 约束，避免越权访问他人导出记录。
 */
@Repository
public interface PerformanceExportTaskRepository extends JpaRepository<PerformanceExportTaskEntity, Long> {

    Optional<PerformanceExportTaskEntity> findByIdAndCreatedBy(Long id, Long createdBy);

    Page<PerformanceExportTaskEntity> findByCreatedByOrderByCreatedAtDesc(Long createdBy, Pageable pageable);

    List<PerformanceExportTaskEntity> findByStatusInOrderByCreatedAtDesc(
            List<PerformanceExportTaskEntity.ExportStatus> statuses);
}
