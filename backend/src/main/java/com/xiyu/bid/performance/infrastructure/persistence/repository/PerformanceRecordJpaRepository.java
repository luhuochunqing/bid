// checkstyle:off
package com.xiyu.bid.performance.infrastructure.persistence.repository;

import com.xiyu.bid.performance.infrastructure.persistence.entity.PerformanceRecordEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface PerformanceRecordJpaRepository
        extends JpaRepository<PerformanceRecordEntity, Long>,
                JpaSpecificationExecutor<PerformanceRecordEntity> {

    @Query("SELECT p FROM PerformanceRecordEntity p WHERE p.expiryDate IS NOT NULL AND p.expiryDate >= :today")
    List<PerformanceRecordEntity> findAllWithExpiryDate(@Param("today") LocalDate today);

    /**
     * CO-583: 集团 → MAX(expiryDate) 聚合查询（基于全量数据，不受筛选条件影响）。
     * 返回 [groupCompany, maxExpiryDate] 二元组列表。
     */
    @Query("SELECT p.groupCompany, MAX(p.expiryDate) FROM PerformanceRecordEntity p "
            + "WHERE p.groupCompany IS NOT NULL AND TRIM(p.groupCompany) <> '' "
            + "AND p.expiryDate IS NOT NULL "
            + "GROUP BY p.groupCompany")
    List<Object[]> findGroupTotalExpiryDates();

    /**
     * CO-583: 单集团 MAX(expiryDate) 查询（详情页用，避免全表聚合）。
     */
    @Query("SELECT MAX(p.expiryDate) FROM PerformanceRecordEntity p "
            + "WHERE p.groupCompany = :groupCompany AND p.expiryDate IS NOT NULL")
    Optional<LocalDate> findGroupTotalExpiryDate(@Param("groupCompany") String groupCompany);

    Optional<PerformanceRecordEntity> findByContractName(String contractName);
}
