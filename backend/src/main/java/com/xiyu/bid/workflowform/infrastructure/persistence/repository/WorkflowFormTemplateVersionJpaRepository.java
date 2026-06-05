package com.xiyu.bid.workflowform.infrastructure.persistence.repository;

import com.xiyu.bid.workflowform.infrastructure.persistence.entity.WorkflowFormTemplateVersionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface WorkflowFormTemplateVersionJpaRepository extends JpaRepository<WorkflowFormTemplateVersionEntity, Long> {
    List<WorkflowFormTemplateVersionEntity> findByTemplateCodeOrderByVersionDesc(String templateCode);

    Optional<WorkflowFormTemplateVersionEntity> findByTemplateCodeAndVersion(String templateCode, Integer version);

    @Query("select coalesce(max(v.version), 0) from WorkflowFormTemplateVersionEntity v where v.templateCode = :templateCode")
    Integer findMaxVersion(@Param("templateCode") String templateCode);

    @Query("""
            select v.templateCode as templateCode, coalesce(max(v.version), 0) as version
            from WorkflowFormTemplateVersionEntity v
            where v.templateCode in :templateCodes
            group by v.templateCode
            """)
    List<WorkflowFormTemplateVersionMaxRow> findMaxVersions(@Param("templateCodes") List<String> templateCodes);
}
