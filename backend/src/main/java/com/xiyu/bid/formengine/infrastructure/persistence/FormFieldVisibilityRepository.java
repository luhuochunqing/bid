// Input: definitionId 查询条件
// Output: 字段可见性规则列表
// Pos: Infrastructure/Persistence 层
// 维护声明: 仅维护 JPA 查询；业务逻辑在 application 层.
package com.xiyu.bid.formengine.infrastructure.persistence;

import com.xiyu.bid.formengine.infrastructure.persistence.entity.FormFieldVisibilityEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FormFieldVisibilityRepository extends JpaRepository<FormFieldVisibilityEntity, Long> {

    /**
     * 查询某个表单定义的所有可见性规则
     */
    List<FormFieldVisibilityEntity> findByDefinitionId(Long definitionId);

    /**
     * 查询某个字段的所有可见性规则
     */
    List<FormFieldVisibilityEntity> findByDefinitionIdAndFieldKey(Long definitionId, String fieldKey);
}
