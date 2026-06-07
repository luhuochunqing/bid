package com.xiyu.bid.templatecatalog.application.service;

import com.xiyu.bid.entity.Template;
import com.xiyu.bid.entity.TemplateVersion;
import com.xiyu.bid.templatecatalog.domain.port.TemplateCatalogRepository;
import com.xiyu.bid.templatecatalog.domain.port.TemplateCatalogVersionRepository;
import com.xiyu.bid.templatecatalog.domain.service.TemplateVersionPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TemplateVersionBootstrapper {

    private final TemplateCatalogRepository templateCatalogRepository;
    private final TemplateCatalogVersionRepository templateCatalogVersionRepository;
    private final TemplateVersionPolicy templateVersionPolicy;

    public Template ensureInitialized(Template template) {
        if (!templateCatalogVersionRepository.findByTemplateIdOrderByCreatedAtDesc(template.getId()).isEmpty()) {
            return template;
        }

        if (template.getCurrentVersion() == null || template.getCurrentVersion().isBlank()) {
            template.setCurrentVersion(templateVersionPolicy.initialVersion());
            template.setFileSize(defaultFileSize(template.getFileSize()));
            template = templateCatalogRepository.save(template);
        }

        templateCatalogVersionRepository.save(TemplateVersion.builder()
                .template(template)
                .version(template.getCurrentVersion())
                .description("初始版本")
                .snapshotName(template.getName())
                .createdBy(template.getCreatedBy())
                .build());
        return template;
    }

    private String defaultFileSize(String fileSize) {
        return fileSize == null || fileSize.isBlank() ? "未知" : fileSize;
    }
}
