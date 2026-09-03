package com.xiyu.bid.scoreparse.repository;

import com.xiyu.bid.scoreparse.entity.ScoreParseFact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ScoreParseFactRepository extends JpaRepository<ScoreParseFact, Long> {
    List<ScoreParseFact> findByParseTaskId(Long parseTaskId);
    List<ScoreParseFact> findByProjectId(Long projectId);
    void deleteByProjectId(Long projectId);
}
