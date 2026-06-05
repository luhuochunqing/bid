package com.xiyu.bid.businessqualification.infrastructure.persistence.repository;

import com.xiyu.bid.businessqualification.infrastructure.persistence.entity.BusinessQualificationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface BusinessQualificationJpaRepository extends JpaRepository<BusinessQualificationEntity, Long> {
    List<BusinessQualificationEntity> findByExpiryDateLessThanEqual(LocalDate expiryDate);
}
