package com.xiyu.bid.templatecatalog.infrastructure.persistence;

import com.xiyu.bid.entity.TemplateDownloadRecord;
import com.xiyu.bid.repository.TemplateDownloadRecordRepository;
import com.xiyu.bid.templatecatalog.domain.port.TemplateCatalogDownloadRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class TemplateCatalogDownloadRecordRepositoryAdapter implements TemplateCatalogDownloadRecordRepository {

    private final TemplateDownloadRecordRepository templateDownloadRecordRepository;

    @Override
    public TemplateDownloadRecord save(TemplateDownloadRecord record) {
        return templateDownloadRecordRepository.save(record);
    }

    @Override
    public long countByTemplateId(Long templateId) {
        return templateDownloadRecordRepository.countByTemplateId(templateId);
    }

    @Override
    public Map<Long, Long> countByTemplateIds(Collection<Long> templateIds) {
        if (templateIds == null || templateIds.isEmpty()) {
            return Map.of();
        }
        return templateDownloadRecordRepository.countGroupedByTemplateIds(templateIds).stream()
                .collect(Collectors.toMap(
                        row -> ((Number) row[0]).longValue(),
                        row -> ((Number) row[1]).longValue(),
                        (left, right) -> right
                ));
    }
}
