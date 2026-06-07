package com.xiyu.bid.templatecatalog.domain.port;

import com.xiyu.bid.entity.TemplateVersion;

import java.util.List;

public interface TemplateCatalogVersionRepository {
    TemplateVersion save(TemplateVersion version);

    List<TemplateVersion> findByTemplateIdOrderByCreatedAtDesc(Long templateId);
}
