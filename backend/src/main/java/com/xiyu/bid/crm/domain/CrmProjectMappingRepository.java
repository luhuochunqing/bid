package com.xiyu.bid.crm.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * CRM 项目映射数据访问
 */
@Repository
public interface CrmProjectMappingRepository extends JpaRepository<CrmProjectMapping, Long> {

    /**
     * 根据业主名称查找映射
     */
    Optional<CrmProjectMapping> findByPurchaserName(String purchaserName);

    /**
     * 根据业主名称查找所有映射（可能有重复数据）
     */
    List<CrmProjectMapping> findAllByPurchaserName(String purchaserName);

    /**
     * 检查业主名称是否存在
     */
    boolean existsByPurchaserName(String purchaserName);
}
