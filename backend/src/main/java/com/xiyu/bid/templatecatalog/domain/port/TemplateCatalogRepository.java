package com.xiyu.bid.templatecatalog.domain.port;

import com.xiyu.bid.entity.Template;
import com.xiyu.bid.templatecatalog.application.command.TemplateQueryCriteria;

import java.util.List;
import java.util.Optional;

public interface TemplateCatalogRepository {
    Template save(Template template);

    Optional<Template> findById(Long id);

    List<Template> findAll(TemplateQueryCriteria criteria);

    boolean existsById(Long id);

    void deleteById(Long id);
}
