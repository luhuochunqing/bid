package com.xiyu.bid.competitionintel.repository;

import com.xiyu.bid.competitionintel.entity.CompetitionAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 竞争分析数据访问接口
 */
@Repository
public interface CompetitionAnalysisRepository extends JpaRepository<CompetitionAnalysis, Long> {

    /**
     * 根据项目ID查找分析记录
     */
    List<CompetitionAnalysis> findByProjectId(Long projectId);

    /**
     * 根据竞争对手ID查找分析记录（按日期倒序）
     */
    List<CompetitionAnalysis> findByCompetitorIdOrderByAnalysisDateDesc(Long competitorId);

    /**
     * 根据项目ID和竞争对手ID查找分析记录
     */
    List<CompetitionAnalysis> findByProjectIdAndCompetitorId(Long projectId, Long competitorId);

    /**
     * 查找项目最新的分析记录
     */
    List<CompetitionAnalysis> findTop1ByProjectIdOrderByAnalysisDateDesc(Long projectId);
}
