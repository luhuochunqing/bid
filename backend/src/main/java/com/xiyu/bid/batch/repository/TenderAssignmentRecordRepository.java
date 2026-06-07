package com.xiyu.bid.batch.repository;

import com.xiyu.bid.batch.entity.TenderAssignmentRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TenderAssignmentRecordRepository extends JpaRepository<TenderAssignmentRecord, Long> {
    List<TenderAssignmentRecord> findByTenderIdOrderByAssignedAtDesc(Long tenderId);
    Optional<TenderAssignmentRecord> findFirstByTenderIdOrderByAssignedAtDesc(Long tenderId);

    @Query("""
            SELECT r FROM TenderAssignmentRecord r
            WHERE r.tenderId IN :tenderIds
            AND r.assignedAt = (
                SELECT MAX(r2.assignedAt) FROM TenderAssignmentRecord r2
                WHERE r2.tenderId = r.tenderId
            )
            """)
    List<TenderAssignmentRecord> findLatestByTenderIds(@Param("tenderIds") Collection<Long> tenderIds);
}
