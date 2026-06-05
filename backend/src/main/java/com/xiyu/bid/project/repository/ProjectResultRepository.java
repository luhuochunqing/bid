// Input: project_result 行
// Output: Spring Data JPA Repository
// Pos: project/repository/
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.project.repository;

import com.xiyu.bid.project.entity.ProjectResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProjectResultRepository extends JpaRepository<ProjectResult, Long> {
    Optional<ProjectResult> findByProjectId(Long projectId);
}
