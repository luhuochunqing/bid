package com.xiyu.bid.scoreparse.repository;

import com.xiyu.bid.scoreparse.entity.ScoreParseReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ScoreParseReviewRepository extends JpaRepository<ScoreParseReview, Long> {
    List<ScoreParseReview> findByParseTaskId(Long parseTaskId);
    List<ScoreParseReview> findByProjectId(Long projectId);
}
