package com.xiyu.bid.templatecatalog.infrastructure.persistence;

import com.xiyu.bid.entity.TemplateVersion;
import com.xiyu.bid.repository.TemplateVersionRepository;
import com.xiyu.bid.templatecatalog.domain.port.TemplateCatalogVersionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class TemplateCatalogVersionRepositoryAdapter implements TemplateCatalogVersionRepository {

    private final TemplateVersionRepository templateVersionRepository;

    @Override
    public TemplateVersion save(TemplateVersion version) {
        return templateVersionRepository.save(version);
    }

    @Override
    public List<TemplateVersion> findByTemplateIdOrderByCreatedAtDesc(Long templateId) {
        return templateVersionRepository.findByTemplateIdOrderByCreatedAtDesc(templateId);
    }
}
