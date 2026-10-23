package com.xiyu.bid.casework.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface CasePrecipitationTaskRepository extends JpaRepository<CasePrecipitationTask, Long> {
    @Query(value = "SELECT id FROM projects WHERE id = :projectId FOR UPDATE", nativeQuery = true)
    Long lockProject(@Param("projectId") Long projectId);

    @Query(value = "SELECT * FROM case_precipitation_task WHERE project_id=:projectId ORDER BY id DESC LIMIT 1 FOR UPDATE", nativeQuery = true)
    Optional<CasePrecipitationTask> findLatestForUpdate(@Param("projectId") Long projectId);

    Optional<CasePrecipitationTask> findFirstByProjectIdOrderByIdDesc(Long projectId);
    Optional<CasePrecipitationTask> findByTaskId(String taskId);

    @Query(value = "SELECT * FROM case_precipitation_task WHERE task_id=:taskId FOR UPDATE", nativeQuery = true)
    Optional<CasePrecipitationTask> findByTaskIdForUpdate(@Param("taskId") String taskId);
}
