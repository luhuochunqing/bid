package com.xiyu.bid.tenderupload.repository;

import com.xiyu.bid.tenderupload.entity.TenderTask;
import com.xiyu.bid.tenderupload.entity.TenderTaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TenderTaskRepository extends JpaRepository<TenderTask, Long> {

    Optional<TenderTask> findByFile_Id(Long fileId);

    @Query(value = """
            select tt.id
            from tender_task tt
            join tender_file tf on tf.id = tt.file_id
            where tt.status in ('QUEUED', 'RETRYING')
              and tt.available_at <= :now
              and (
                  select count(1)
                  from tender_task running
                  join tender_file running_file on running.file_id = running_file.id
                  where running.status = 'RUNNING'
                    and running_file.user_id = tf.user_id
              ) < :perUserLimit
            order by tt.priority asc, tt.created_at asc
            limit :limit
            for update skip locked
            """, nativeQuery = true)
    List<Long> claimRunnableTaskIds(@Param("now") LocalDateTime now,
                                    @Param("limit") int limit,
                                    @Param("perUserLimit") int perUserLimit);

    @Query("""
            select count(t)
            from TenderTask t
            where t.status = :status
            """)
    long countByStatus(@Param("status") TenderTaskStatus status);

    @Query(value = """
            select count(1)
            from tender_task t
            where t.status in ('QUEUED', 'RETRYING')
              and t.available_at <= :now
              and t.priority <= :priority
            """, nativeQuery = true)
    long countApproximateQueueDepth(@Param("now") LocalDateTime now,
                                    @Param("priority") int priority);

    long countByStatusInAndAvailableAtLessThanEqual(Collection<TenderTaskStatus> statuses, LocalDateTime availableAt);
}
