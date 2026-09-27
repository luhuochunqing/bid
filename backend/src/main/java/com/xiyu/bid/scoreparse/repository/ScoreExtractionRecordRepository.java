package com.xiyu.bid.scoreparse.repository;
import com.xiyu.bid.scoreparse.entity.ScoreExtractionRecord;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ScoreExtractionRecordRepository extends JpaRepository<ScoreExtractionRecord,Long> {
    @org.springframework.data.jpa.repository.Query(value="SELECT parse_task_id FROM score_extraction_snapshot WHERE parse_task_id=:taskId FOR UPDATE",nativeQuery=true)
    java.util.Optional<Long> currentIdForUpdate(@org.springframework.data.repository.query.Param("taskId") Long taskId);
}
