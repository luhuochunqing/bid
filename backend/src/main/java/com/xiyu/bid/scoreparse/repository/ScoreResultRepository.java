package com.xiyu.bid.scoreparse.repository;

import com.xiyu.bid.scoreparse.entity.ScoreResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 打分结果 Repository（spec 041）。
 */
@Repository
public interface ScoreResultRepository extends JpaRepository<ScoreResult, Long> {

    /** 按项目查打分结果（联 score_item.project_id，用于结果展示） */
    List<ScoreResult> findByScoreItemIdIn(List<Long> scoreItemIds);

    /** 重新打分前整批覆盖（FR-021：事务内 DELETE + INSERT） */
    void deleteByScoreItemIdIn(List<Long> scoreItemIds);
}
