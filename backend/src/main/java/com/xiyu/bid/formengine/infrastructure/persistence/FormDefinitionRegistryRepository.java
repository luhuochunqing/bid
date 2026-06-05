// Input: scope / orgId 查询条件
// Output: 表单定义实体列表
// Pos: Infrastructure/Persistence 层
// 维护声明: 仅维护 JPA 查询；业务逻辑在 application 层.
package com.xiyu.bid.formengine.infrastructure.persistence;

import com.xiyu.bid.formengine.infrastructure.persistence.entity.FormDefinitionRegistryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FormDefinitionRegistryRepository extends JpaRepository<FormDefinitionRegistryEntity, Long> {

    /**
     * 查询全局模板（orgId 为 null）
     */
    @Query("SELECT d FROM FormDefinitionRegistryEntity d WHERE d.scope = :scope AND d.orgId IS NULL AND d.enabled = true")
    Optional<FormDefinitionRegistryEntity> findByScopeAndOrgIdIsNullAndEnabledTrue(@Param("scope") String scope);

    /**
     * 查询租户级模板
     */
    @Query("SELECT d FROM FormDefinitionRegistryEntity d WHERE d.scope = :scope AND d.orgId = :orgId AND d.enabled = true")
    Optional<FormDefinitionRegistryEntity> findByScopeAndOrgIdAndEnabledTrue(
            @Param("scope") String scope,
            @Param("orgId") Long orgId);

    /**
     * 检查 scope 是否已存在
     */
    boolean existsByScope(String scope);
}
