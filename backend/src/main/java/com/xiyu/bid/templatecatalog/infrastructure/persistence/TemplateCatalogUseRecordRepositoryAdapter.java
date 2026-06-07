// Input: domain use-record port calls
// Output: JPA-backed use-record persistence and project-scoped aggregate counts
// Pos: Infrastructure Adapter/基础设施适配层
package com.xiyu.bid.templatecatalog.infrastructure.persistence;

import com.xiyu.bid.entity.TemplateUseRecord;
import com.xiyu.bid.repository.TemplateUseRecordRepository;
import com.xiyu.bid.templatecatalog.domain.port.TemplateCatalogUseRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class TemplateCatalogUseRecordRepositoryAdapter implements TemplateCatalogUseRecordRepository {

    private final TemplateUseRecordRepository templateUseRecordRepository;

    @Override
    public TemplateUseRecord save(TemplateUseRecord record) {
        return templateUseRecordRepository.save(record);
    }

    @Override
    public long countByTemplateId(Long templateId) {
        return templateUseRecordRepository.countByTemplateId(templateId);
    }

    @Override
    public Map<Long, Long> countByTemplateIds(Collection<Long> templateIds) {
        if (templateIds == null || templateIds.isEmpty()) {
            return Map.of();
        }
        return toCountMap(templateUseRecordRepository.countGroupedByTemplateIds(templateIds));
    }

    @Override
    public long countByTemplateIdVisibleToProjects(Long templateId, Collection<Long> projectIds) {
        if (projectIds == null || projectIds.isEmpty()) {
            return templateUseRecordRepository.countByTemplateIdAndProjectIdIsNull(templateId);
        }
        return templateUseRecordRepository.countByTemplateIdAndVisibleProjectIds(templateId, projectIds);
    }

    @Override
    public Map<Long, Long> countByTemplateIdsVisibleToProjects(Collection<Long> templateIds, Collection<Long> projectIds) {
        if (templateIds == null || templateIds.isEmpty()) {
            return Map.of();
        }
        if (projectIds == null || projectIds.isEmpty()) {
            return toCountMap(templateUseRecordRepository.countGroupedByTemplateIdsAndProjectIdIsNull(templateIds));
        }
        return toCountMap(templateUseRecordRepository.countGroupedByTemplateIdsAndVisibleProjectIds(templateIds, projectIds));
    }

    private Map<Long, Long> toCountMap(List<Object[]> rows) {
        return rows.stream()
                .collect(Collectors.toMap(
                        row -> ((Number) row[0]).longValue(),
                        row -> ((Number) row[1]).longValue(),
                        (left, right) -> right
                ));
    }
}
