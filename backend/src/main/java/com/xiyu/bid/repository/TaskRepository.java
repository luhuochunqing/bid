package com.xiyu.bid.repository;

import com.xiyu.bid.entity.Task;
import com.xiyu.bid.project.core.ProjectStage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    @Query("SELECT DISTINCT t.projectId FROM Task t WHERE t.assigneeId = :assigneeId")
    List<Long> findDistinctProjectIdsByAssigneeId(@Param("assigneeId") Long assigneeId);

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
     * CO-361: 根据项目ID和受托人ID查找任务（项目负责人/执行人只看自己的任务）
     */
    List<Task> findByProjectIdAndAssigneeId(Long projectId, Long assigneeId);

    /**
     * CO-361: 判断当前用户是否为该项目的任务执行人（用于文档查看放行）。
     * 派生查询，仅返回 boolean，避免加载完整 Task 实体。
     */
    boolean existsByProjectIdAndAssigneeId(Long projectId, Long assigneeId);

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
     * CO-533: 查找在指定时间窗口内到期且状态非指定值的任务（即将到期扫描）。
     */
    List<Task> findByDueDateBetweenAndStatusNot(LocalDateTime start, LocalDateTime end, Task.Status status);

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

    /**
     * 工作台角色化改造：根据受托人ID和项目阶段查找任务（JOIN Project）。
     * 用于工作台任务待办只显示标书制作阶段（DRAFTING）任务的场景。
     * <p>使用 JOIN 而非 IN 子查询：MySQL 对 IN (subquery) 不一定优化为 semi-join，
     * Project 表数据量大时会退化为逐行相关子查询。</p>
     * @param assigneeId 受托人ID
     * @param projectStage 项目阶段枚举（类型安全，杜绝字符串拼写错误）
     */
    default List<Task> findByAssigneeIdAndProjectStage(Long assigneeId, ProjectStage projectStage) {
        return findByAssigneeIdAndProjectStageName(assigneeId, projectStage.name());
    }

    /** 底层查询：stage 在 Project 实体中以 String 存储，枚举 name() 转换收口在 default 方法。 */
    @Query("SELECT t FROM Task t JOIN Project p ON t.projectId = p.id " +
           "WHERE t.assigneeId = :assigneeId AND p.stage = :projectStage")
    List<Task> findByAssigneeIdAndProjectStageName(@Param("assigneeId") Long assigneeId,
                                                   @Param("projectStage") String projectStage);
}
