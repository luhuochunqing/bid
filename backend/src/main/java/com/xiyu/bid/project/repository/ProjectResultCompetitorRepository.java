// Input: project_result_competitor 行
// Output: Spring Data JPA Repository
// Pos: project/repository/
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.project.repository;

import com.xiyu.bid.project.entity.ProjectResultCompetitor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProjectResultCompetitorRepository extends JpaRepository<ProjectResultCompetitor, Long> {

    List<ProjectResultCompetitor> findByResultIdOrderBySortOrderAsc(Long resultId);

    void deleteByResultId(Long resultId);
}
