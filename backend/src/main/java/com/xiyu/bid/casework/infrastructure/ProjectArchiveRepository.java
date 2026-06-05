package com.xiyu.bid.casework.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProjectArchiveRepository extends JpaRepository<ProjectArchive, Long>, JpaSpecificationExecutor<ProjectArchive> {
    Optional<ProjectArchive> findByProjectId(Long projectId);

    long countByArchiveStatus(String archiveStatus);

    long countByProjectIdIn(List<Long> projectIds);

    long countByProjectIdInAndArchiveStatus(List<Long> projectIds, String archiveStatus);
}
