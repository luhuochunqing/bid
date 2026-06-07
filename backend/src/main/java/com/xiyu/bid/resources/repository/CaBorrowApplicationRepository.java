package com.xiyu.bid.resources.repository;

import com.xiyu.bid.resources.entity.CaBorrowApplicationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CaBorrowApplicationRepository extends JpaRepository<CaBorrowApplicationEntity, Long> {

    List<CaBorrowApplicationEntity> findByCaCertificateIdOrderByCreatedAtDesc(Long caCertificateId);

    List<CaBorrowApplicationEntity> findByApplicantIdOrderByCreatedAtDesc(Long applicantId);

    List<CaBorrowApplicationEntity> findByApproverIdAndStatus(Long approverId, String status);

    List<CaBorrowApplicationEntity> findByCaCertificateIdAndStatus(Long caCertificateId, String status);
}
