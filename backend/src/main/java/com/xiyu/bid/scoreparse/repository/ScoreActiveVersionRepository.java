package com.xiyu.bid.scoreparse.repository;
import com.xiyu.bid.scoreparse.entity.ScoreActiveVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface ScoreActiveVersionRepository extends JpaRepository<ScoreActiveVersion,Long> {
    Optional<ScoreActiveVersion> findFirstByProjectIdOrderByIdDesc(Long projectId);
    boolean existsByProjectId(Long projectId);
    @org.springframework.data.jpa.repository.Query(value="SELECT id FROM score_active_version WHERE project_id=:projectId ORDER BY id DESC LIMIT 1 FOR UPDATE",nativeQuery=true)
    Optional<Long> currentIdForUpdate(@org.springframework.data.repository.query.Param("projectId") Long projectId);
}
