package com.xiyu.bid.approval.repository;

import com.xiyu.bid.approval.entity.ApprovalRequest;
import com.xiyu.bid.approval.enums.ApprovalStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 审批请求Repository
 */
@Repository
public interface ApprovalRequestRepository extends JpaRepository<ApprovalRequest, UUID>,
        JpaSpecificationExecutor<ApprovalRequest> {

    /**
     * 根据项目ID查找审批请求
     */
    List<ApprovalRequest> findByProjectIdOrderByCreatedAtDesc(Long projectId);

    /**
     * 根据状态查找审批请求
     */
    List<ApprovalRequest> findByStatusOrderByCreatedAtDesc(ApprovalStatus status);

    /**
     * 根据申请人查找
     */
    List<ApprovalRequest> findByRequesterIdOrderByCreatedAtDesc(Long requesterId);

    /**
     * 查找待审批的请求
     */
    List<ApprovalRequest> findByStatusOrderByPriorityDescCreatedAtDesc(ApprovalStatus status);

    /**
     * 查找当前审批人的待审批请求
     */
    List<ApprovalRequest> findByStatusAndCurrentApproverIdOrderByPriorityDescCreatedAtDesc(
            ApprovalStatus status, Long approverId);

    /**
     * 统计各状态的审批数量
     */
    @Query("SELECT ar.status, COUNT(ar) FROM ApprovalRequest ar GROUP BY ar.status")
    List<Object[]> countByStatus();

    /**
     * 统计今日提交的审批数量
     */
    Long countBySubmittedAtBetween(LocalDateTime start, LocalDateTime end);

    /**
     * 统计本月提交的审批数量
     */
    Long countBySubmittedAtBetweenAndStatusIn(LocalDateTime start, LocalDateTime end, List<ApprovalStatus> statuses);

    /**
     * 统计超期的审批数量
     */
    Long countByStatusAndDueDateBefore(ApprovalStatus status, LocalDateTime now);

    /**
     * 统计临近截止的审批数量 (24小时内)
     */
    Long countByStatusAndDueDateBetween(ApprovalStatus status, LocalDateTime start, LocalDateTime end);

    /**
     * 计算平均处理时长
     */
    List<ApprovalRequest> findByStatusInAndCompletedAtIsNotNull(List<ApprovalStatus> statuses);

    /**
     * 统计按类型分组的数量
     */
    @Query("SELECT ar.approvalType, COUNT(ar) FROM ApprovalRequest ar GROUP BY ar.approvalType")
    List<Object[]> countByType();

    /**
     * 统计按优先级分组的数量
     */
    @Query("SELECT ar.priority, COUNT(ar) FROM ApprovalRequest ar GROUP BY ar.priority")
    List<Object[]> countByPriority();

    /**
     * 查找项目的最新审批请求
     */
    Optional<ApprovalRequest> findFirstByProjectIdOrderByCreatedAtDesc(Long projectId);
}
