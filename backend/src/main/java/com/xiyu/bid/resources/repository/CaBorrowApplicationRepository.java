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

    // CO-459: 管理员查看全部借用申请（不限状态）
    List<CaBorrowApplicationEntity> findAllByOrderByCreatedAtDesc();

    // CO-459: 按审批人查询全部申请（不限状态）
    List<CaBorrowApplicationEntity> findByApproverIdOrderByCreatedAtDesc(Long approverId);

    // CO-459: 按状态查询（数据库层面过滤，避免内存过滤）
    List<CaBorrowApplicationEntity> findByStatusOrderByCreatedAtDesc(String status);
}
