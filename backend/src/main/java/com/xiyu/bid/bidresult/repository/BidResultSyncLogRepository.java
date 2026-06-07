package com.xiyu.bid.bidresult.repository;

import com.xiyu.bid.bidresult.entity.BidResultSyncLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BidResultSyncLogRepository extends JpaRepository<BidResultSyncLog, Long> {
    Optional<BidResultSyncLog> findFirstByOperationTypeOrderByCreatedAtDesc(BidResultSyncLog.OperationType operationType);
}
