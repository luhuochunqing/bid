package com.xiyu.bid.businessqualification.infrastructure.persistence.repository;

import com.xiyu.bid.businessqualification.domain.valueobject.QualificationStatus;
import com.xiyu.bid.businessqualification.infrastructure.persistence.entity.BusinessQualificationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface BusinessQualificationJpaRepository extends JpaRepository<BusinessQualificationEntity, Long> {

    List<BusinessQualificationEntity> findByExpiryDateLessThanEqual(LocalDate expiryDate);

    /**
     * §4.1.3.8 蓝图：扫描即将到期但未下架的资质。
     * 排除已下架（status=RETIRED）的证书。
     */
    List<BusinessQualificationEntity> findByExpiryDateLessThanEqualAndStatusNot(
            LocalDate expiryDate, QualificationStatus excludedStatus);

    /**
     * §4.1.3.4 蓝图：按证书编号查重（导入时行级校验）。
     */
    boolean existsByCertificateNo(String certificateNo);

    /**
     * §4.2.1.2 蓝图：获取所有已录入的等级列表（去重，非空）。
     */
    List<String> findDistinctLevelByLevelIsNotNull();

    /**
     * §4.2.1.4 蓝图：按证书编号查找资质（批量关联附件时匹配）。
     */
    Optional<BusinessQualificationEntity> findByCertificateNo(String certificateNo);
}
