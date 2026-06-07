package com.xiyu.bid.businessqualification.infrastructure.persistence.repository;

import com.xiyu.bid.businessqualification.infrastructure.persistence.entity.BusinessQualificationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface BusinessQualificationJpaRepository extends JpaRepository<BusinessQualificationEntity, Long> {
    List<BusinessQualificationEntity> findByExpiryDateLessThanEqual(LocalDate expiryDate);

    /**
     * §4.1.3.8 蓝图：扫描即将到期但未下架的资质。
     * 排除已下架（status=RETIRED）的证书。
     */
    List<BusinessQualificationEntity> findByExpiryDateLessThanEqualAndStatusNot(
            LocalDate expiryDate, com.xiyu.bid.businessqualification.domain.valueobject.QualificationStatus excludedStatus);
}
