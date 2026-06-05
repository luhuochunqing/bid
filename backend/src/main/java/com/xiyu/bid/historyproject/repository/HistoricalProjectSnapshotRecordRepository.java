package com.xiyu.bid.historyproject.repository;

import com.xiyu.bid.historyproject.entity.HistoricalProjectSnapshotRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface HistoricalProjectSnapshotRecordRepository extends JpaRepository<HistoricalProjectSnapshotRecord, Long> {

    Optional<HistoricalProjectSnapshotRecord> findTopByProjectIdOrderByCapturedAtDesc(Long projectId);
}
