// Input: TemplateUseRecord JPA queries
// Output: persisted records and aggregate counts, including project-scoped visibility counts
// Pos: Repository/JPA 仓储层
package com.xiyu.bid.repository;

import com.xiyu.bid.entity.TemplateUseRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface TemplateUseRecordRepository extends JpaRepository<TemplateUseRecord, Long> {
    long countByTemplateId(Long templateId);

    @Query("select record.template.id, count(record) from TemplateUseRecord record where record.template.id in :templateIds group by record.template.id")
    List<Object[]> countGroupedByTemplateIds(Collection<Long> templateIds);

    long countByTemplateIdAndProjectIdIsNull(Long templateId);

    @Query("""
            select count(record)
            from TemplateUseRecord record
            where record.template.id = :templateId
              and (record.projectId is null or record.projectId in :projectIds)
            """)
    long countByTemplateIdAndVisibleProjectIds(@Param("templateId") Long templateId, @Param("projectIds") Collection<Long> projectIds);

    @Query("""
            select record.template.id, count(record)
            from TemplateUseRecord record
            where record.template.id in :templateIds
              and record.projectId is null
            group by record.template.id
            """)
    List<Object[]> countGroupedByTemplateIdsAndProjectIdIsNull(@Param("templateIds") Collection<Long> templateIds);

    @Query("""
            select record.template.id, count(record)
            from TemplateUseRecord record
            where record.template.id in :templateIds
              and (record.projectId is null or record.projectId in :projectIds)
            group by record.template.id
            """)
    List<Object[]> countGroupedByTemplateIdsAndVisibleProjectIds(
            @Param("templateIds") Collection<Long> templateIds,
            @Param("projectIds") Collection<Long> projectIds
    );
}
