package com.xiyu.bid.casework.repository;

import com.xiyu.bid.casework.entity.CaseShareRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CaseShareRecordRepository extends JpaRepository<CaseShareRecord, Long> {

    List<CaseShareRecord> findByCaseIdOrderByCreatedAtDesc(Long caseId);
}
