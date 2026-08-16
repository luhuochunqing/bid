package com.xiyu.bid.scoreparse.repository;

import com.xiyu.bid.scoreparse.entity.ScoreItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 评分项 Repository（spec 041）。
 */
@Repository
public interface ScoreItemRepository extends JpaRepository<ScoreItem, Long> {

    /** 按项目查询全部评分项（item_index 升序，用于清单展示与汇总） */
    List<ScoreItem> findByProjectIdOrderByItemIndexAsc(Long projectId);

    /** 重新解析前按项目清理旧行（FR-021 覆盖语义） */
    void deleteByProjectId(Long projectId);
}
