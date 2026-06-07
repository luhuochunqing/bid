// Input: TaskComment entity queries
// Output: repository methods for task comment timeline
// Pos: Repository/任务评论数据访问
package com.xiyu.bid.task.repository;

import com.xiyu.bid.task.entity.TaskComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TaskCommentRepository extends JpaRepository<TaskComment, Long> {

    List<TaskComment> findByTaskIdOrderByCreatedAtDesc(Long taskId);
}
