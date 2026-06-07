// Input: TaskHistory entity queries and archive cutoff
// Output: repository methods for task history timeline and archival
// Pos: Repository/任务历史数据访问
package com.xiyu.bid.task.repository;

import com.xiyu.bid.task.entity.TaskHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TaskHistoryRepository extends JpaRepository<TaskHistory, Long> {

    List<TaskHistory> findByTaskIdAndArchivedAtIsNullOrderByCreatedAtDesc(Long taskId);

    List<TaskHistory> findByTaskIdOrderByCreatedAtDesc(Long taskId);

    @Modifying
    @Query("""
        UPDATE TaskHistory h
           SET h.archivedAt = :archivedAt
         WHERE h.archivedAt IS NULL
           AND h.createdAt < :cutoff
        """)
    int archiveBefore(@Param("cutoff") LocalDateTime cutoff, @Param("archivedAt") LocalDateTime archivedAt);
}
