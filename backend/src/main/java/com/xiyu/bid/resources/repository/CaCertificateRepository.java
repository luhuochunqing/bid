package com.xiyu.bid.resources.repository;

import com.xiyu.bid.resources.entity.CaCertificateEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public interface CaCertificateRepository extends JpaRepository<CaCertificateEntity, Long>, JpaSpecificationExecutor<CaCertificateEntity> {

    List<CaCertificateEntity> findByCustodianId(Long custodianId);

    long countByBorrowStatus(String borrowStatus);

    long countByStatus(String status);

    @Query("SELECT new map(" +
            "COUNT(c) as total, " +
            "SUM(CASE WHEN c.status = 'EXPIRING' THEN 1 ELSE 0 END) as expiring, " +
            "SUM(CASE WHEN c.status = 'EXPIRED' THEN 1 ELSE 0 END) as expired, " +
            "SUM(CASE WHEN c.borrowStatus = 'BORROWED' THEN 1 ELSE 0 END) as borrowed) " +
            "FROM CaCertificateEntity c WHERE c.status <> 'INACTIVE'")
    Map<String, Long> getOverviewAggregated();
}
