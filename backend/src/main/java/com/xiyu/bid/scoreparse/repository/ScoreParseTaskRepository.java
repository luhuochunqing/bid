package com.xiyu.bid.scoreparse.repository;

import com.xiyu.bid.scoreparse.entity.ScoreParseTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * AI 评分解析/打分异步任务 Repository（spec 041）。
 */
@Repository
public interface ScoreParseTaskRepository extends JpaRepository<ScoreParseTask, Long> {

    /** 按业务任务 ID（UUID）查询 */
    Optional<ScoreParseTask> findByTaskId(String taskId);

    /**
     * 互斥校验：同 project_id + task_type 下的非终态任务（PENDING/PROCESSING）。
     * <p>FR-019：同项目同类型仅允许一个非终态任务；重复触发返回进行中任务。
     */
    List<ScoreParseTask> findByProjectIdAndTaskTypeAndStatusIn(Long projectId, String taskType,
                                                               List<String> statuses);

    /**
     * 超时扫描：状态为 PROCESSING 且 updated_at 早于阈值时间。
     * <p>默认阈值 30min（app.score-parse.timeout-minutes），置 FAILED + timeout_marked=1。
     */
    List<ScoreParseTask> findByStatusAndUpdatedAtBefore(String status, LocalDateTime threshold);
}
