// checkstyle:off
package com.xiyu.bid.performance.infrastructure.persistence.repository;

import com.xiyu.bid.performance.domain.valueobject.CustomerType;
import com.xiyu.bid.performance.domain.valueobject.ProjectType;
import com.xiyu.bid.performance.infrastructure.persistence.entity.PerformanceRecordEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface PerformanceRecordJpaRepository extends JpaRepository<PerformanceRecordEntity, Long> {

    @Query("SELECT p FROM PerformanceRecordEntity p WHERE " +
           "(:keyword IS NULL OR p.contractName LIKE %:keyword% OR p.signingEntity LIKE %:keyword% OR p.groupCompany LIKE %:keyword%) AND " +
           "(:customerType IS NULL OR p.customerType = :customerType) AND " +
           "(:projectType IS NULL OR p.projectType = :projectType) AND " +
           "(:territory IS NULL OR p.territory LIKE %:territory%) AND " +
           "(:signingDateStart IS NULL OR p.signingDate >= :signingDateStart) AND " +
           "(:signingDateEnd IS NULL OR p.signingDate <= :signingDateEnd) AND " +
           "(:expiryDateStart IS NULL OR p.expiryDate >= :expiryDateStart) AND " +
           "(:expiryDateEnd IS NULL OR p.expiryDate <= :expiryDateEnd) AND " +
           "(:hasBidNotice IS NULL OR p.hasBidNotice = :hasBidNotice) AND " +
           "(:projectManagerKeyword IS NULL OR p.xiyuProjectManager LIKE %:projectManagerKeyword%) AND " +
           "(:status IS NULL OR " +
           "  (:status = 'EXPIRED' AND p.expiryDate < :today) OR " +
           "  (:status = 'EXPIRING' AND p.expiryDate >= :today AND ( " +
           "    (p.customerType = 'CENTRAL_SOE' AND p.expiryDate <= :expiringDateSOE) OR " +
           "    (p.customerType <> 'CENTRAL_SOE' AND p.expiryDate <= :expiringDateDefault) " +
           "  )) OR " +
           "  (:status = 'IN_PERFORMANCE' AND (p.expiryDate IS NULL OR (p.expiryDate >= :today AND ( " +
           "    (p.customerType = 'CENTRAL_SOE' AND p.expiryDate > :expiringDateSOE) OR " +
           "    (p.customerType <> 'CENTRAL_SOE' AND p.expiryDate > :expiringDateDefault) " +
           "  )))))")
    List<PerformanceRecordEntity> findByCriteria(
            @Param("keyword") String keyword,
            @Param("customerType") CustomerType customerType,
            @Param("projectType") ProjectType projectType,
            @Param("status") String status,
            @Param("territory") String territory,
            @Param("signingDateStart") LocalDate signingDateStart,
            @Param("signingDateEnd") LocalDate signingDateEnd,
            @Param("expiryDateStart") LocalDate expiryDateStart,
            @Param("expiryDateEnd") LocalDate expiryDateEnd,
            @Param("hasBidNotice") Boolean hasBidNotice,
            @Param("projectManagerKeyword") String projectManagerKeyword,
            @Param("today") LocalDate today,
            @Param("expiringDateSOE") LocalDate expiringDateSOE,
            @Param("expiringDateDefault") LocalDate expiringDateDefault);


    @Query("SELECT p FROM PerformanceRecordEntity p WHERE " +
           "(:keyword IS NULL OR p.contractName LIKE %:keyword% OR p.signingEntity LIKE %:keyword% OR p.groupCompany LIKE %:keyword%) AND " +
           "(:customerType IS NULL OR p.customerType = :customerType) AND " +
           "(:projectType IS NULL OR p.projectType = :projectType) AND " +
           "(:territory IS NULL OR p.territory LIKE %:territory%) AND " +
           "(:signingDateStart IS NULL OR p.signingDate >= :signingDateStart) AND " +
           "(:signingDateEnd IS NULL OR p.signingDate <= :signingDateEnd) AND " +
           "(:expiryDateStart IS NULL OR p.expiryDate >= :expiryDateStart) AND " +
           "(:expiryDateEnd IS NULL OR p.expiryDate <= :expiryDateEnd) AND " +
           "(:hasBidNotice IS NULL OR p.hasBidNotice = :hasBidNotice) AND " +
           "(:projectManagerKeyword IS NULL OR p.xiyuProjectManager LIKE %:projectManagerKeyword%) AND " +
           "(:status IS NULL OR " +
           "  (:status = 'EXPIRED' AND p.expiryDate < :today) OR " +
           "  (:status = 'EXPIRING' AND p.expiryDate >= :today AND ( " +
           "    (p.customerType = 'CENTRAL_SOE' AND p.expiryDate <= :expiringDateSOE) OR " +
           "    (p.customerType <> 'CENTRAL_SOE' AND p.expiryDate <= :expiringDateDefault) " +
           "  )) OR " +
           "  (:status = 'IN_PERFORMANCE' AND (p.expiryDate IS NULL OR (p.expiryDate >= :today AND ( " +
           "    (p.customerType = 'CENTRAL_SOE' AND p.expiryDate > :expiringDateSOE) OR " +
           "    (p.customerType <> 'CENTRAL_SOE' AND p.expiryDate > :expiringDateDefault) " +
           "  )))))")
    Page<PerformanceRecordEntity> findByCriteriaPageable(
            @Param("keyword") String keyword,
            @Param("customerType") CustomerType customerType,
            @Param("projectType") ProjectType projectType,
            @Param("status") String status,
            @Param("territory") String territory,
            @Param("signingDateStart") LocalDate signingDateStart,
            @Param("signingDateEnd") LocalDate signingDateEnd,
            @Param("expiryDateStart") LocalDate expiryDateStart,
            @Param("expiryDateEnd") LocalDate expiryDateEnd,
            @Param("hasBidNotice") Boolean hasBidNotice,
            @Param("projectManagerKeyword") String projectManagerKeyword,
            @Param("today") LocalDate today,
            @Param("expiringDateSOE") LocalDate expiringDateSOE,
            @Param("expiringDateDefault") LocalDate expiringDateDefault,
            Pageable pageable);

    @Query("SELECT p FROM PerformanceRecordEntity p WHERE p.expiryDate IS NOT NULL AND p.expiryDate >= :today")
    List<PerformanceRecordEntity> findAllWithExpiryDate(@Param("today") LocalDate today);
}
