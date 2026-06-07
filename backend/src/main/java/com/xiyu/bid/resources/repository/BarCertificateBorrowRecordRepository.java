package com.xiyu.bid.resources.repository;

import com.xiyu.bid.resources.entity.BarCertificateBorrowRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BarCertificateBorrowRecordRepository extends JpaRepository<BarCertificateBorrowRecord, Long> {

    List<BarCertificateBorrowRecord> findByCertificateIdOrderByBorrowedAtDesc(Long certificateId);
}
