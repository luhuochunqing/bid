package com.xiyu.bid.approval.repository;

import com.xiyu.bid.approval.entity.ApprovalAction;
import com.xiyu.bid.approval.enums.ApprovalActionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 审批操作记录Repository
 */
@Repository
public interface ApprovalActionRepository extends JpaRepository<ApprovalAction, UUID> {

    /**
     * 根据审批请求ID查找所有操作记录
     */
    List<ApprovalAction> findByApprovalRequestIdOrderByActionTimeAsc(UUID approvalRequestId);

    /**
     * 根据审批请求ID和操作类型查找
     */
    List<ApprovalAction> findByApprovalRequestIdAndActionTypeOrderByActionTimeAsc(
            UUID approvalRequestId, ApprovalActionType actionType);

    /**
     * 查找用户的操作记录
     */
    List<ApprovalAction> findByActorIdOrderByActionTimeDesc(Long actorId);

    /**
     * 统计审批请求的操作次数
     */
    @Query("SELECT COUNT(aa) FROM ApprovalAction aa WHERE aa.approvalRequestId = :requestId")
    Long countByApprovalRequestId(@Param("requestId") UUID requestId);

    /**
     * 查找指定时间范围内的操作记录
     */
    @Query("SELECT aa FROM ApprovalAction aa WHERE aa.actionTime BETWEEN :start AND :end ORDER BY aa.actionTime DESC")
    List<ApprovalAction> findByActionTimeBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /**
     * 查找最新的操作记录
     */
    @Query("SELECT aa FROM ApprovalAction aa WHERE aa.approvalRequestId = :requestId ORDER BY aa.actionTime DESC")
    List<ApprovalAction> findLatestByApprovalRequestId(@Param("requestId") UUID requestId);
}
