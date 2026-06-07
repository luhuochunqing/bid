package com.xiyu.bid.bidresult.repository;

import com.xiyu.bid.bidresult.entity.CompetitorWinRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface CompetitorWinRecordRepository extends JpaRepository<CompetitorWinRecord, Long> {
    List<CompetitorWinRecord> findAllByOrderByWonAtDesc();
    List<CompetitorWinRecord> findByProjectIdOrderByWonAtDesc(Long projectId);

    @Query("SELECT COUNT(DISTINCT c.competitorId) FROM CompetitorWinRecord c")
    long countDistinctCompetitors();
}
