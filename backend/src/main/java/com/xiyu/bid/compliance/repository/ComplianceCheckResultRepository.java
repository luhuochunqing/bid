package com.xiyu.bid.compliance.repository;

import com.xiyu.bid.compliance.entity.ComplianceCheckResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 合规检查结果数据访问接口.
 */
@Repository
public interface ComplianceCheckResultRepository
        extends JpaRepository<ComplianceCheckResult, Long> {

    /**
     * 根据项目ID查找所有检查结果（按时间倒序）.
     *
     * @param projectId 项目ID
     * @return 结果列表
     */
    List<ComplianceCheckResult> findByProjectIdOrderByCheckedAtDesc(
            Long projectId);

    /**
     * 根据项目ID查找检查结果.
     *
     * @param projectId 项目ID
     * @return 结果列表
     */
    List<ComplianceCheckResult> findByProjectId(Long projectId);

    /**
     * 根据标书ID查找所有检查结果（按时间倒序）.
     *
     * @param tenderId 标书ID
     * @return 结果列表
     */
    List<ComplianceCheckResult> findByTenderIdOrderByCheckedAtDesc(
            Long tenderId);

    /**
     * 根据标书ID查找检查结果.
     *
     * @param tenderId 标书ID
     * @return 结果列表
     */
    List<ComplianceCheckResult> findByTenderId(Long tenderId);

    /**
     * 根据项目ID查找最新的检查结果.
     *
     * @param projectId 项目ID
     * @return 最新结果
     */
    Optional<ComplianceCheckResult> findTopByProjectIdOrderByCheckedAtDesc(
            Long projectId);

    /**
     * 根据标书ID查找最新的检查结果.
     *
     * @param tenderId 标书ID
     * @return 最新结果
     */
    Optional<ComplianceCheckResult> findTopByTenderIdOrderByCheckedAtDesc(
            Long tenderId);

    /**
     * 根据合规状态查找检查结果.
     *
     * @param status 状态
     * @return 结果列表
     */
    List<ComplianceCheckResult> findByOverallStatus(
            ComplianceCheckResult.Status status);

    /**
     * 根据项目ID和检查类型查找最新的检查结果.
     *
     * @param projectId 项目ID
     * @param checkType 检查类型
     * @return 最新结果
     */
    Optional<ComplianceCheckResult>
        findTopByProjectIdAndCheckTypeOrderByCheckedAtDesc(
                Long projectId, ComplianceCheckResult.CheckType checkType);
}
