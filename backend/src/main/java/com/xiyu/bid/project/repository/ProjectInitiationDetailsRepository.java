// Input: project_initiation_details 行
// Output: Spring Data JPA Repository
// Pos: project/repository/
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.project.repository;

import com.xiyu.bid.project.entity.ProjectInitiationDetails;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProjectInitiationDetailsRepository extends JpaRepository<ProjectInitiationDetails, Long> {
    Optional<ProjectInitiationDetails> findByProjectId(Long projectId);

    List<ProjectInitiationDetails> findByProjectIdIn(Collection<Long> projectIds);

    /** CO-361: 按项目负责人 userId 查询其发起立项的项目详情（owner_user_id）。 */
    List<ProjectInitiationDetails> findByOwnerUserId(Long ownerUserId);
}
