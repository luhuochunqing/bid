// Input: project_lead_assignment 行
// Output: Spring Data JPA Repository
// Pos: project/repository/
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.project.repository;

import com.xiyu.bid.project.entity.ProjectLeadAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProjectLeadAssignmentRepository extends JpaRepository<ProjectLeadAssignment, Long> {
    Optional<ProjectLeadAssignment> findByProjectId(Long projectId);
    List<ProjectLeadAssignment> findByProjectIdIn(Collection<Long> projectIds);
    List<ProjectLeadAssignment> findByPrimaryLeadUserId(Long userId);
    List<ProjectLeadAssignment> findBySecondaryLeadUserId(Long userId);

    /**
     * 解析项目负责人 ID 数组 [primaryLeadUserId, secondaryLeadUserId]。
     * <p>避免调用方重复实现此逻辑。</p>
     *
     * @param projectId 项目 ID
     * @return [primaryLeadUserId, secondaryLeadUserId]，不存在时返回 [null, null]
     */
    default Long[] resolveLeadIdsByProjectId(Long projectId) {
        return findByProjectId(projectId)
                .map(a -> new Long[]{a.getPrimaryLeadUserId(), a.getSecondaryLeadUserId()})
                .orElse(new Long[]{null, null});
    }
}
