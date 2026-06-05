package com.xiyu.bid.brandauth.infrastructure.persistence.repository;

import com.xiyu.bid.brandauth.domain.valueobject.AuthorizationStatus;
import com.xiyu.bid.brandauth.infrastructure.persistence.entity.BrandAuthorizationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface BrandAuthorizationJpaRepository extends JpaRepository<BrandAuthorizationEntity, Long> {

    List<BrandAuthorizationEntity> findByBrandName(String brandName);

    List<BrandAuthorizationEntity> findByStatus(AuthorizationStatus status);

    @Query("SELECT b FROM BrandAuthorizationEntity b WHERE b.endDate IS NOT NULL AND b.endDate <= :threshold AND b.status != 'ARCHIVED'")
    List<BrandAuthorizationEntity> findExpiringByThreshold(@Param("threshold") LocalDate threshold);
}
