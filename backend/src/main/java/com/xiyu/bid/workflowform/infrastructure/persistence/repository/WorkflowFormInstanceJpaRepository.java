package com.xiyu.bid.workflowform.infrastructure.persistence.repository;

import com.xiyu.bid.workflowform.infrastructure.persistence.entity.WorkflowFormInstanceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface WorkflowFormInstanceJpaRepository extends JpaRepository<WorkflowFormInstanceEntity, Long> {
    Optional<WorkflowFormInstanceEntity> findByOaInstanceId(String oaInstanceId);

    @Modifying
    @Query("""
            update WorkflowFormInstanceEntity entity
               set entity.status = com.xiyu.bid.workflowform.domain.WorkflowFormStatus.OA_APPROVING,
                   entity.oaInstanceId = :oaInstanceId
             where entity.id = :id
               and entity.status = com.xiyu.bid.workflowform.domain.WorkflowFormStatus.SUBMITTED
            """)
    int markOaApprovingIfSubmitted(@Param("id") Long id, @Param("oaInstanceId") String oaInstanceId);

    @Modifying
    @Query("""
            update WorkflowFormInstanceEntity entity
               set entity.status = com.xiyu.bid.workflowform.domain.WorkflowFormStatus.OA_FAILED,
                   entity.oaInstanceId = :oaInstanceId,
                   entity.businessApplyError = :reason
             where entity.id = :id
               and entity.status in (
                   com.xiyu.bid.workflowform.domain.WorkflowFormStatus.SUBMITTED,
                   com.xiyu.bid.workflowform.domain.WorkflowFormStatus.OA_STARTING,
                   com.xiyu.bid.workflowform.domain.WorkflowFormStatus.OA_APPROVING
               )
            """)
    int markOaFailedIfNotTerminal(@Param("id") Long id, @Param("oaInstanceId") String oaInstanceId, @Param("reason") String reason);

    @Modifying
    @Query("""
            update WorkflowFormInstanceEntity entity
               set entity.status = com.xiyu.bid.workflowform.domain.WorkflowFormStatus.OA_APPROVED,
                   entity.oaOperatorName = :operatorName,
                   entity.oaComment = :comment
             where entity.id = :id
               and entity.status = com.xiyu.bid.workflowform.domain.WorkflowFormStatus.OA_APPROVING
            """)
    int markOaApprovedIfApproving(@Param("id") Long id, @Param("operatorName") String operatorName, @Param("comment") String comment);

    @Modifying
    @Query("""
            update WorkflowFormInstanceEntity entity
               set entity.status = com.xiyu.bid.workflowform.domain.WorkflowFormStatus.OA_REJECTED,
                   entity.oaOperatorName = :operatorName,
                   entity.oaComment = :comment
             where entity.id = :id
               and entity.status = com.xiyu.bid.workflowform.domain.WorkflowFormStatus.OA_APPROVING
            """)
    int markOaRejectedIfApproving(@Param("id") Long id, @Param("operatorName") String operatorName, @Param("comment") String comment);

    @Modifying
    @Query("""
            update WorkflowFormInstanceEntity entity
               set entity.status = com.xiyu.bid.workflowform.domain.WorkflowFormStatus.BUSINESS_APPLIED,
                   entity.businessApplied = true,
                   entity.businessApplyError = null
             where entity.id = :id
               and entity.status = com.xiyu.bid.workflowform.domain.WorkflowFormStatus.OA_APPROVED
            """)
    int markBusinessAppliedIfOaApproved(@Param("id") Long id);
}
