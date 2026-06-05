package com.xiyu.bid.templatecatalog.domain.port;

import com.xiyu.bid.entity.TemplateDownloadRecord;

import java.util.Collection;
import java.util.Map;

public interface TemplateCatalogDownloadRecordRepository {
    TemplateDownloadRecord save(TemplateDownloadRecord record);

    long countByTemplateId(Long templateId);

    Map<Long, Long> countByTemplateIds(Collection<Long> templateIds);
}
