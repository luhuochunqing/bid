package com.xiyu.bid.qualification.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface DepositTrackingRepository extends JpaRepository<DepositTracking, Long> {

    @Query("SELECT SUM(d.amount) FROM DepositTracking d")
    BigDecimal sumTotalAmount();

    @Query("SELECT SUM(d.amount) FROM DepositTracking d WHERE d.status = 'PAID'")
    BigDecimal sumPendingAmount();

    @Query("SELECT COUNT(d) FROM DepositTracking d WHERE d.status = 'PAID'")
    long countPendingDeposits();

    List<DepositTracking> findAllByOrderByPaymentDateDesc();
}
