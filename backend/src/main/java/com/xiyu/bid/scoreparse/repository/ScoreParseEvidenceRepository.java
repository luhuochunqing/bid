package com.xiyu.bid.scoreparse.repository;

import com.xiyu.bid.scoreparse.entity.ScoreParseEvidence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ScoreParseEvidenceRepository extends JpaRepository<ScoreParseEvidence, Long> {
    List<ScoreParseEvidence> findByParseTaskId(Long parseTaskId);
    List<ScoreParseEvidence> findByFactId(String factId);
    void deleteByParseTaskId(Long parseTaskId);
}
