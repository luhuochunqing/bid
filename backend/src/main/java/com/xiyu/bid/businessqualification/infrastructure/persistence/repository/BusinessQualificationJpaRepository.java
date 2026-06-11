package com.xiyu.bid.businessqualification.infrastructure.persistence.repository;

import com.xiyu.bid.businessqualification.domain.valueobject.QualificationStatus;
import com.xiyu.bid.businessqualification.infrastructure.persistence.entity.BusinessQualificationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface BusinessQualificationJpaRepository
        extends JpaRepository<BusinessQualificationEntity, Long>,
                JpaSpecificationExecutor<BusinessQualificationEntity> {

    /**
     * Scan all certificate-no-matching records (may be multiple, e.g. same no. with history).
     */
    List<BusinessQualificationEntity> findAllByCertificateNo(String certificateNo);
    List<BusinessQualificationEntity> findByExpiryDateLessThanEqual(LocalDate expiryDate);

    /**
     * Blueprint §4.1.3.8: scan expiring-but-not-retired qualifications.
     * Excludes retired (status=RETIRED) certificates.
     */
    List<BusinessQualificationEntity> findByExpiryDateLessThanEqualAndStatusNot(
            LocalDate expiryDate, QualificationStatus excludedStatus);

    /**
     * Blueprint §4.1.3.4: dedupe by certificate no (per-row validation on import).
     */
    boolean existsByCertificateNo(String certificateNo);

    /**
     * Blueprint §4.2.1.2: get all recorded levels (dedup, non-null).
     */
    List<String> findDistinctLevelByLevelIsNotNull();

    /**
     * Blueprint §4.2.1.4: find by certificate no (batch attachment matching).
     */
    Optional<BusinessQualificationEntity> findByCertificateNo(String certificateNo);
}
