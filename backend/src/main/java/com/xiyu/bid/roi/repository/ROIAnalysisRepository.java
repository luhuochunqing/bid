package com.xiyu.bid.roi.repository;

import com.xiyu.bid.roi.entity.ROIAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * ROI分析Repository
 * 提供ROI分析数据的数据库操作
 */
@Repository
public interface ROIAnalysisRepository extends JpaRepository<ROIAnalysis, Long> {

    /**
     * 根据项目ID查找ROI分析
     * @param projectId 项目ID
     * @return ROI分析数据（如果存在）
     */
    Optional<ROIAnalysis> findFirstByProjectIdOrderByAnalysisDateDesc(Long projectId);

    default Optional<ROIAnalysis> findByProjectId(Long projectId) {
        return findFirstByProjectIdOrderByAnalysisDateDesc(projectId);
    }
}
