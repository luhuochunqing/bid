// Input: definitionId 查询条件
// Output: 字段条件逻辑列表
// Pos: Infrastructure/Persistence 层
// 维护声明: 仅维护 JPA 查询；业务逻辑在 application 层.
package com.xiyu.bid.formengine.infrastructure.persistence;

import com.xiyu.bid.formengine.infrastructure.persistence.entity.FormFieldConditionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FormFieldConditionRepository extends JpaRepository<FormFieldConditionEntity, Long> {

    /**
     * 查询某个表单定义的所有条件规则
     */
    List<FormFieldConditionEntity> findByDefinitionId(Long definitionId);
}
