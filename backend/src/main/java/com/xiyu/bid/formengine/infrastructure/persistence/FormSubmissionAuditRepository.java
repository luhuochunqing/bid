// Input: 审计日志查询条件
// Output: 审计记录列表
// Pos: Infrastructure/Persistence 层
// 维护声明: 仅维护 JPA 查询；业务逻辑在 application 层.
package com.xiyu.bid.formengine.infrastructure.persistence;

import com.xiyu.bid.formengine.infrastructure.persistence.entity.FormSubmissionAuditEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FormSubmissionAuditRepository extends JpaRepository<FormSubmissionAuditEntity, Long> {

    /**
     * 分页查询某个表单的所有提交记录
     */
    Page<FormSubmissionAuditEntity> findByDefinitionIdOrderByCreatedAtDesc(Long definitionId, Pageable pageable);

    /**
     * 查询某个操作人的所有提交记录
     */
    List<FormSubmissionAuditEntity> findByOperatorUsernameOrderByCreatedAtDesc(String operatorUsername);

    /**
     * 查询某个租户的所有提交记录
     */
    Page<FormSubmissionAuditEntity> findByOrgIdOrderByCreatedAtDesc(Long orgId, Pageable pageable);

    /**
     * 查询某个表单 + 状态的所有记录
     */
    List<FormSubmissionAuditEntity> findByDefinitionIdAndStatusOrderByCreatedAtDesc(
            Long definitionId,
            String status);
}
