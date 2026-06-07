package com.xiyu.bid.resources.repository;

import com.xiyu.bid.resources.entity.BarAsset;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;

@Repository
public interface BarAssetRepository extends JpaRepository<BarAsset, Long> {

    Page<BarAsset> findByType(BarAsset.AssetType type, Pageable pageable);

    Page<BarAsset> findByStatus(BarAsset.AssetStatus status, Pageable pageable);

    Page<BarAsset> findByValueBetween(BigDecimal minValue, BigDecimal maxValue, Pageable pageable);

    Page<BarAsset> findByAcquireDateBetween(LocalDate startDate, LocalDate endDate, Pageable pageable);

    Page<BarAsset> searchByNameContainingIgnoreCase(@Param("keyword") String keyword, Pageable pageable);

    @Query("SELECT COALESCE(SUM(a.value), 0) FROM BarAsset a")
    BigDecimal sumTotalValue();

    long countByType(BarAsset.AssetType type);
}
