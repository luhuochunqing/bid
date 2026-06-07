// Input: definitionId 查询条件
// Output: 跨字段验证规则列表
// Pos: Infrastructure/Persistence 层
// 维护声明: 仅维护 JPA 查询；业务逻辑在 application 层.
package com.xiyu.bid.formengine.infrastructure.persistence;

import com.xiyu.bid.formengine.infrastructure.persistence.entity.CrossFieldValidationRuleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CrossFieldValidationRuleRepository extends JpaRepository<CrossFieldValidationRuleEntity, Long> {

    /**
     * 查询某个表单定义的所有跨字段验证规则，按 priority 升序
     */
    List<CrossFieldValidationRuleEntity> findByDefinitionIdOrderByPriorityAsc(Long definitionId);

    /**
     * 查询某个表单定义+scope 的所有跨字段验证规则
     */
    List<CrossFieldValidationRuleEntity> findByDefinitionIdAndScopeOrderByPriorityAsc(Long definitionId, String scope);
}
