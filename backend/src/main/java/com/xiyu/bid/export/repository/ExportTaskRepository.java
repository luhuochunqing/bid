package com.xiyu.bid.export.repository;

import com.xiyu.bid.export.entity.ExportTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for ExportTask entity.
 * Provides data access for export task tracking and management.
 */
@Repository
public interface ExportTaskRepository extends JpaRepository<ExportTask, Long> {

    /**
     * Find export tasks by user ID.
     */
    List<ExportTask> findByCreatedByOrderByCreatedAtDesc(Long userId);

    /**
     * Find export tasks by status.
     */
    List<ExportTask> findByStatusOrderByCreatedAtDesc(ExportTask.TaskStatus status);

    /**
     * Find export tasks by user ID and status.
     */
    List<ExportTask> findByCreatedByAndStatusOrderByCreatedAtDesc(Long userId, ExportTask.TaskStatus status);

    /**
     * Find export tasks by data type and user ID.
     */
    List<ExportTask> findByDataTypeAndCreatedByOrderByCreatedAtDesc(String dataType, Long userId);

    /**
     * Find export task by its ID and user ID (for authorization check).
     */
    Optional<ExportTask> findByIdAndCreatedBy(Long id, Long userId);

    /**
     * Delete expired export tasks.
     */
    @Query("DELETE FROM ExportTask e WHERE e.expiresAt < :expiryDate")
    int deleteExpiredTasks(@Param("expiryDate") LocalDateTime expiryDate);

    /**
     * Count export tasks by user ID within a time period (for rate limiting).
     */
    @Query("SELECT COUNT(e) FROM ExportTask e WHERE e.createdBy = :userId AND e.createdAt >= :since")
    long countByUserIdAndCreatedAtAfter(@Param("userId") Long userId, @Param("since") LocalDateTime since);

    /**
     * Find pending or processing export tasks by user ID.
     */
    @Query("SELECT e FROM ExportTask e WHERE e.createdBy = :userId AND e.status IN ('PENDING', 'PROCESSING')")
    List<ExportTask> findActiveTasksByUserId(@Param("userId") Long userId);
}
