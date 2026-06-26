package com.xiyu.bid.businessqualification.infrastructure.persistence.repository;

import com.xiyu.bid.businessqualification.domain.valueobject.QualificationStatus;
import com.xiyu.bid.businessqualification.infrastructure.persistence.entity.BusinessQualificationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /**
     * CO-358 fix: 部分更新下架状态，仅修改 retired / retire_reason / status / updated_at。
     * 不走全量 save，避免触发附件 delete+saveAll 导致的序列化异常或 NOT NULL 约束违反。
     */
    @Modifying
    @Query("UPDATE BusinessQualificationEntity q SET q.retired = :retired, q.retireReason = :retireReason, q.status = :status, q.updatedAt = CURRENT_TIMESTAMP WHERE q.id = :id")
    int updateRetiredStatus(@Param("id") Long id,
                            @Param("retired") boolean retired,
                            @Param("retireReason") String retireReason,
                            @Param("status") QualificationStatus status);
}
