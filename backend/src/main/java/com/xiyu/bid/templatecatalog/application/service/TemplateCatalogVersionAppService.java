package com.xiyu.bid.templatecatalog.application.service;

import com.xiyu.bid.entity.Template;
import com.xiyu.bid.exception.ResourceNotFoundException;
import com.xiyu.bid.templatecatalog.application.mapper.TemplateVersionDtoMapper;
import com.xiyu.bid.templatecatalog.application.view.TemplateCatalogVersionView;
import com.xiyu.bid.templatecatalog.domain.port.TemplateCatalogRepository;
import com.xiyu.bid.templatecatalog.domain.port.TemplateCatalogVersionRepository;
import com.xiyu.bid.templatecatalog.domain.service.TemplateVersionPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TemplateCatalogVersionAppService {

    private final TemplateCatalogRepository templateCatalogRepository;
    private final TemplateCatalogVersionRepository templateCatalogVersionRepository;
    private final TemplateVersionDtoMapper templateVersionDtoMapper;
    private final TemplateVersionPolicy templateVersionPolicy;

    @Transactional(readOnly = true)
    public List<TemplateCatalogVersionView> list(Long id) {
        Template template = templateCatalogRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Template", id.toString()));

        List<TemplateCatalogVersionView> versions = templateCatalogVersionRepository.findByTemplateIdOrderByCreatedAtDesc(id).stream()
                .map(templateVersionDtoMapper::toDto)
                .toList();
        if (!versions.isEmpty()) {
            return versions;
        }
        return List.of(TemplateCatalogVersionView.builder()
                .version(resolveVersion(template.getCurrentVersion()))
                .description("历史模板基线版本")
                .snapshotName(template.getName())
                .createdBy(template.getCreatedBy())
                .createdAt(template.getCreatedAt())
                .build());
    }

    private String resolveVersion(String currentVersion) {
        if (currentVersion == null || currentVersion.isBlank()) {
            return templateVersionPolicy.initialVersion();
        }
        return currentVersion;
    }
}
