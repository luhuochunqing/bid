package com.xiyu.bid.casework.repository;

import com.xiyu.bid.casework.entity.CaseReferenceRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CaseReferenceRecordRepository extends JpaRepository<CaseReferenceRecord, Long> {

    List<CaseReferenceRecord> findByCaseIdOrderByReferencedAtDesc(Long caseId);
}
