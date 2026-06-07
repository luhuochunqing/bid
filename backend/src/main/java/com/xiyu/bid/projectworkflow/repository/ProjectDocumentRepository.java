package com.xiyu.bid.projectworkflow.repository;

import com.xiyu.bid.projectworkflow.entity.ProjectDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ProjectDocumentRepository extends JpaRepository<ProjectDocument, Long> {

    List<ProjectDocument> findByProjectIdOrderByCreatedAtDesc(Long projectId);

    List<ProjectDocument> findByProjectIdInOrderByCreatedAtDesc(Collection<Long> projectIds);

    /**
     * 按关联实体类型和 ID 查询文档。
     */
    List<ProjectDocument> findByLinkedEntityTypeAndLinkedEntityIdOrderByCreatedAtDesc(
            String linkedEntityType, Long linkedEntityId);

    /** 判重：检查指定关联实体的文档是否已存在（用于幂等归集）。 */
    boolean existsByLinkedEntityTypeAndLinkedEntityId(String linkedEntityType, Long linkedEntityId);

    @Query("""
            select d
            from ProjectDocument d
            where d.projectId = :projectId
              and (:documentCategory is null or d.documentCategory = :documentCategory)
              and (:linkedEntityType is null or d.linkedEntityType = :linkedEntityType)
              and (:linkedEntityId is null or d.linkedEntityId = :linkedEntityId)
            order by d.createdAt desc
            """)
    List<ProjectDocument> findByProjectIdAndFiltersOrderByCreatedAtDesc(
            @Param("projectId") Long projectId,
            @Param("documentCategory") String documentCategory,
            @Param("linkedEntityType") String linkedEntityType,
            @Param("linkedEntityId") Long linkedEntityId);
}
