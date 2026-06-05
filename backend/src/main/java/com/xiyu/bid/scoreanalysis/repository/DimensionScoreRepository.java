package com.xiyu.bid.scoreanalysis.repository;

import com.xiyu.bid.scoreanalysis.entity.DimensionScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 维度分数数据访问接口
 * 提供维度分数的数据操作方法
 */
@Repository
public interface DimensionScoreRepository extends JpaRepository<DimensionScore, Long> {

    /**
     * 根据分析ID查找所有维度分数
     * @param analysisId 分析ID
     * @return 维度分数列表
     */
    List<DimensionScore> findByAnalysisId(Long analysisId);
}
