package com.xiyu.bid.scoreparse.repository;

import com.xiyu.bid.scoreparse.entity.ScoreResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 打分结果 Repository（spec 041）。
 */
@Repository
public interface ScoreResultRepository extends JpaRepository<ScoreResult, Long> {

    /** 按项目查打分结果（联 score_item.project_id，用于结果展示） */
    List<ScoreResult> findByScoreItemIdIn(List<Long> scoreItemIds);

    /**
     * 重新打分前整批覆盖（FR-021：事务内 DELETE + INSERT）。
     * <p>必须用 bulk delete：派生 delete 会把 DELETE 排进 Hibernate ActionQueue，
     * flush 顺序 INSERT 先于 DELETE，重新打分撞 uk_sr_score_item 唯一约束
     * （spec 048 T020 场景 2 实证：Duplicate entry '1' for key 'score_result.uk_sr_score_item'）。
     */
    @Modifying
    @Query("delete from ScoreResult r where r.scoreItemId in :ids")
    void deleteByScoreItemIdIn(@Param("ids") List<Long> scoreItemIds);
}
