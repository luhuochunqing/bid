package com.xiyu.bid.task.repository;

import com.xiyu.bid.task.entity.TaskDeliverable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Repository for TaskDeliverable entities.
 */
@Repository
public interface TaskDeliverableRepository
        extends JpaRepository<TaskDeliverable, Long> {

    /**
     * Find deliverables for a task, newest first.
     *
     * @param taskId the task id
     * @return ordered list of deliverables
     */
    List<TaskDeliverable> findByTaskIdOrderByCreatedAtDesc(
            Long taskId);

    /**
     * Find deliverables for multiple tasks.
     *
     * @param taskIds collection of task ids
     * @return all matching deliverables
     */
    List<TaskDeliverable> findByTaskIdIn(Collection<Long> taskIds);

    /**
     * Count deliverables for a task.
     *
     * @param taskId the task id
     * @return count of deliverables
     */
    long countByTaskId(Long taskId);

    /**
     * Count deliverables of a specific type for a task.
     *
     * @param taskId the task id
     * @param type   the deliverable type
     * @return count of matching deliverables
     */
    long countByTaskIdAndDeliverableType(
            Long taskId, TaskDeliverable.DeliverableType type);

    /**
     * Delete all deliverables for a task.
     *
     * @param taskId the task id
     */
    void deleteByTaskId(Long taskId);

    /**
     * Find the latest version deliverable for a task.
     *
     * @param taskId the task id
     * @return optional containing the newest version
     */
    Optional<TaskDeliverable> findFirstByTaskIdOrderByVersionDesc(
            Long taskId);
}
