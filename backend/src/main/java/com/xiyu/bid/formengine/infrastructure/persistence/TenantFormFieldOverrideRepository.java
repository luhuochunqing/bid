// Input: definitionId + orgId 查询条件
// Output: 租户字段覆盖列表
// Pos: Infrastructure/Persistence 层
// 维护声明: 仅维护 JPA 查询；业务逻辑在 application 层.
package com.xiyu.bid.formengine.infrastructure.persistence;

import com.xiyu.bid.formengine.infrastructure.persistence.entity.TenantFormFieldOverrideEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TenantFormFieldOverrideRepository extends JpaRepository<TenantFormFieldOverrideEntity, Long> {

    /**
     * 查询某个表单定义 + 租户的所有字段覆盖规则
     */
    List<TenantFormFieldOverrideEntity> findByDefinitionIdAndOrgId(Long definitionId, Long orgId);

    /**
     * 查询某个字段的覆盖规则
     */
    List<TenantFormFieldOverrideEntity> findByDefinitionIdAndOrgIdAndFieldKey(Long definitionId, Long orgId, String fieldKey);
}
