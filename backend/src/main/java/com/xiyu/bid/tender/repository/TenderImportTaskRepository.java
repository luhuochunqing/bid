package com.xiyu.bid.tender.repository;

import com.xiyu.bid.tender.entity.TenderImportTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 标讯批量导入异步任务 Repository。
 */
@Repository
public interface TenderImportTaskRepository extends JpaRepository<TenderImportTask, Long> {

    /** 按业务任务 ID（UUID）查询 */
    Optional<TenderImportTask> findByTaskId(String taskId);

    /**
     * 查找卡死任务：状态为 PROCESSING 且 updated_at 早于阈值时间。
     * <p>用于服务重启后恢复或标记失败（30min 阈值，见 contracts/tender-import-task-states.md）。
     */
    List<TenderImportTask> findByStatusAndUpdatedAtBefore(String status, LocalDateTime threshold);
}
