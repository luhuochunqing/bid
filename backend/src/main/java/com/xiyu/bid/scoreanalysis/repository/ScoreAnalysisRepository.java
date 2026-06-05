package com.xiyu.bid.scoreanalysis.repository;

import com.xiyu.bid.scoreanalysis.entity.ScoreAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 评分分析数据访问接口
 * 提供评分分析的数据操作方法
 */
@Repository
public interface ScoreAnalysisRepository extends JpaRepository<ScoreAnalysis, Long> {

    /**
     * 根据项目ID查找所有评分分析
     * @param projectId 项目ID
     * @return 评分分析列表
     */
    List<ScoreAnalysis> findByProjectId(Long projectId);

    /**
     * 根据项目ID查找最新的评分分析
     * @param projectId 项目ID
     * @return 最新的评分分析
     */
    Optional<ScoreAnalysis> findFirstByProjectIdOrderByAnalysisDateDesc(Long projectId);

    /**
     * 根据项目ID查找所有评分分析，按日期倒序排列
     * @param projectId 项目ID
     * @return 评分分析列表（按日期倒序）
     */
    List<ScoreAnalysis> findByProjectIdOrderByAnalysisDateDesc(Long projectId);

    /**
     * 根据标讯ID查找最新的评分分析
     * @param tenderId 标讯ID
     * @return 最新的评分分析
     */
    Optional<ScoreAnalysis> findFirstByTenderIdOrderByAnalysisDateDesc(Long tenderId);

    /**
     * 根据标讯ID查找所有评分分析，按日期倒序排列
     * @param tenderId 标讯ID
     * @return 评分分析列表（按日期倒序）
     */
    List<ScoreAnalysis> findByTenderIdOrderByAnalysisDateDesc(Long tenderId);
}
