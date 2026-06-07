// Input: template ids and optional visible project scope
// Output: persisted use records and aggregate use counts
// Pos: Domain Port/领域端口
package com.xiyu.bid.templatecatalog.domain.port;

import com.xiyu.bid.entity.TemplateUseRecord;

import java.util.Collection;
import java.util.Map;

public interface TemplateCatalogUseRecordRepository {
    TemplateUseRecord save(TemplateUseRecord record);

    long countByTemplateId(Long templateId);

    Map<Long, Long> countByTemplateIds(Collection<Long> templateIds);

    long countByTemplateIdVisibleToProjects(Long templateId, Collection<Long> projectIds);

    Map<Long, Long> countByTemplateIdsVisibleToProjects(Collection<Long> templateIds, Collection<Long> projectIds);
}
