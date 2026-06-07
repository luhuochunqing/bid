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

    Optional<PerformanceRecordEntity> findByContractName(String contractName);
}
