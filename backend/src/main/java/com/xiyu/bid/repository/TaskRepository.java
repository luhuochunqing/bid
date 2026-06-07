package com.xiyu.bid.repository;

import com.xiyu.bid.entity.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 任务Repository接口
 */
@Repository
public interface TaskRepository extends JpaRepository<Task, Long> {

    /**
     * 根据项目ID查找任务
     */
    List<Task> findByProjectId(Long projectId);

    List<Task> findByProjectIdIn(Collection<Long> projectIds);

    /**
     * 根据受托人ID查找任务
     */
    List<Task> findByAssigneeId(Long assigneeId);

    List<Task> findByAssigneeIdIn(Collection<Long> assigneeIds);

    /**
     * 根据状态查找任务
     */
    List<Task> findByStatus(Task.Status status);

    /**
     * 根据优先级查找任务
     */
    List<Task> findByPriority(Task.Priority priority);

    /**
     * 根据项目ID和状态查找任务
     */
    List<Task> findByProjectIdAndStatus(Long projectId, Task.Status status);

    /**
     * 根据受托人ID和状态查找任务
     */
    List<Task> findByAssigneeIdAndStatus(Long assigneeId, Task.Status status);

    /**
     * 查找在指定日期之前到期的任务
     */
    List<Task> findByDueDateBefore(LocalDateTime date);

    /**
     * 查找已过期但未完成的任务
     */
    List<Task> findByDueDateBeforeAndStatusNot(LocalDateTime date, Task.Status status);

    /**
     * 统计项目的任务数量
     */
    Long countByProjectId(Long projectId);

    /**
     * 统计受托人的任务数量
     */
    Long countByAssigneeId(Long assigneeId);

    /**
     * 删除项目的所有任务
     */
    void deleteByProjectId(Long projectId);

    long countByProjectIdAndStatus(Long projectId, Task.Status status);

    List<Task> findByProjectIdAndStatusIn(Long projectId, Collection<Task.Status> statuses);
}
