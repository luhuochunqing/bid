package com.xiyu.bid.documentexport.repository;

import com.xiyu.bid.documentexport.entity.DocumentArchiveRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DocumentArchiveRecordRepository extends JpaRepository<DocumentArchiveRecord, Long> {

    List<DocumentArchiveRecord> findByProjectIdOrderByArchivedAtDesc(Long projectId);
}
