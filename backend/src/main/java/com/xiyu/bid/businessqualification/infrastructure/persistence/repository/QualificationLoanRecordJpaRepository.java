package com.xiyu.bid.businessqualification.infrastructure.persistence.repository;

import com.xiyu.bid.businessqualification.infrastructure.persistence.entity.QualificationLoanRecordEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface QualificationLoanRecordJpaRepository extends JpaRepository<QualificationLoanRecordEntity, Long> {
    Optional<QualificationLoanRecordEntity> findFirstByQualificationIdAndReturnedAtIsNullOrderByBorrowedAtDesc(Long qualificationId);

    List<QualificationLoanRecordEntity> findByQualificationIdOrderByBorrowedAtDesc(Long qualificationId);

    List<QualificationLoanRecordEntity> findAllByOrderByBorrowedAtDesc();
}
