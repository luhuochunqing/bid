package com.xiyu.bid.brandauth.manufacturer.infrastructure.persistence.repository;

import com.xiyu.bid.brandauth.manufacturer.infrastructure.persistence.entity.BrandAuthOperationLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BrandAuthOperationLogJpaRepository extends JpaRepository<BrandAuthOperationLogEntity, Long> {
    List<BrandAuthOperationLogEntity> findByAuthorizationIdOrderByCreatedAtDesc(Long authorizationId);
}
