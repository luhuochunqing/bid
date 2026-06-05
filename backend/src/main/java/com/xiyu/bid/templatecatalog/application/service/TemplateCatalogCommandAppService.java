package com.xiyu.bid.templatecatalog.application.service;

import com.xiyu.bid.entity.Template;
import com.xiyu.bid.entity.TemplateVersion;
import com.xiyu.bid.exception.ResourceNotFoundException;
import com.xiyu.bid.templatecatalog.application.command.TemplateCatalogCopyCommand;
import com.xiyu.bid.templatecatalog.application.command.TemplateCatalogMutationCommand;
import com.xiyu.bid.templatecatalog.application.mapper.TemplateDtoMapper;
import com.xiyu.bid.templatecatalog.application.view.TemplateCatalogView;
import com.xiyu.bid.templatecatalog.domain.port.TemplateCatalogDownloadRecordRepository;
import com.xiyu.bid.templatecatalog.domain.port.TemplateCatalogRepository;
import com.xiyu.bid.templatecatalog.domain.port.TemplateCatalogUseRecordRepository;
import com.xiyu.bid.templatecatalog.domain.port.TemplateCatalogVersionRepository;
import com.xiyu.bid.templatecatalog.domain.service.TemplateClassificationPolicy;
import com.xiyu.bid.templatecatalog.domain.service.TemplateCatalogValidationResult;
import com.xiyu.bid.templatecatalog.domain.service.TemplateVersionPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TemplateCatalogCommandAppService {

    private final TemplateCatalogRepository templateCatalogRepository;
    private final TemplateCatalogVersionRepository templateCatalogVersionRepository;
    private final TemplateCatalogUseRecordRepository templateCatalogUseRecordRepository;
    private final TemplateCatalogDownloadRecordRepository templateCatalogDownloadRecordRepository;
    private final TemplateClassificationPolicy templateClassificationPolicy;
    private final TemplateVersionPolicy templateVersionPolicy;
    private final TemplateDtoMapper templateDtoMapper;
    private final TemplateVersionBootstrapper templateVersionBootstrapper;

    @Transactional
    public TemplateCatalogView create(TemplateCatalogMutationCommand command) {
        requireValidClassification(
                templateClassificationPolicy.validateComplete(
                        command.getProductType(),
                        command.getIndustry(),
                        command.getDocumentType()
                )
        );
        Template template = templateCatalogRepository.save(Template.builder()
                .name(command.getName())
                .category(command.getCategory())
                .productType(toStoredValue(command.getProductType()))
                .industry(toStoredValue(command.getIndustry()))
                .documentType(toStoredValue(command.getDocumentType()))
                .fileUrl(command.getFileUrl())
                .description(command.getDescription())
                .currentVersion(templateVersionPolicy.initialVersion())
                .fileSize(defaultFileSize(command.getFileSize()))
                .tags(copyTags(command.getTags()))
                .createdBy(command.getCreatedBy())
                .build());
        templateCatalogVersionRepository.save(TemplateVersion.builder()
                .template(template)
                .version(template.getCurrentVersion())
                .description("初始版本")
                .snapshotName(template.getName())
                .createdBy(template.getCreatedBy())
                .build());
        return toDto(template);
    }

    @Transactional
    public TemplateCatalogView update(Long id, TemplateCatalogMutationCommand command) {
        Template existing = templateCatalogRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Template", id.toString()));
        templateVersionBootstrapper.ensureInitialized(existing);

        String resolvedProductType = command.getProductType() != null ? toStoredValue(command.getProductType()) : existing.getProductType();
        String resolvedIndustry = command.getIndustry() != null ? toStoredValue(command.getIndustry()) : existing.getIndustry();
        String resolvedDocumentType = command.getDocumentType() != null ? toStoredValue(command.getDocumentType()) : existing.getDocumentType();
        Template updated = Template.builder()
                .id(existing.getId())
                .name(command.getName() != null ? command.getName() : existing.getName())
                .category(command.getCategory() != null ? command.getCategory() : existing.getCategory())
                .productType(resolvedProductType)
                .industry(resolvedIndustry)
                .documentType(resolvedDocumentType)
                .fileUrl(command.getFileUrl() != null ? command.getFileUrl() : existing.getFileUrl())
                .description(command.getDescription() != null ? command.getDescription() : existing.getDescription())
                .currentVersion(templateVersionPolicy.nextVersion(existing.getCurrentVersion()))
                .fileSize(command.getFileSize() != null ? command.getFileSize() : defaultFileSize(existing.getFileSize()))
                .tags(command.getTags() != null ? copyTags(command.getTags()) : copyTags(existing.getTags()))
                .createdBy(existing.getCreatedBy())
                .createdAt(existing.getCreatedAt())
                .updatedAt(existing.getUpdatedAt())
                .build();
        requireValidClassification(
                templateClassificationPolicy.validateComplete(
                        command.getProductType() != null ? command.getProductType() : com.xiyu.bid.templatecatalog.domain.valueobject.ProductType.fromValue(updated.getProductType()),
                        command.getIndustry() != null ? command.getIndustry() : com.xiyu.bid.templatecatalog.domain.valueobject.IndustryType.fromValue(updated.getIndustry()),
                        command.getDocumentType() != null ? command.getDocumentType() : com.xiyu.bid.templatecatalog.domain.valueobject.DocumentType.fromValue(updated.getDocumentType())
                )
        );
        updated = templateCatalogRepository.save(updated);
        templateCatalogVersionRepository.save(TemplateVersion.builder()
                .template(updated)
                .version(updated.getCurrentVersion())
                .description("模板更新")
                .snapshotName(updated.getName())
                .createdBy(updated.getCreatedBy())
                .build());
        return toDto(updated);
    }

    @Transactional
    public void delete(Long id) {
        if (!templateCatalogRepository.existsById(id)) {
            throw new ResourceNotFoundException("Template", id.toString());
        }
        templateCatalogRepository.deleteById(id);
    }

    @Transactional
    public TemplateCatalogView copy(Long id, TemplateCatalogCopyCommand command) {
        Template source = templateCatalogRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Template", id.toString()));
        templateVersionBootstrapper.ensureInitialized(source);

        Template copied = templateCatalogRepository.save(Template.builder()
                .name(command.getName())
                .category(source.getCategory())
                .productType(source.getProductType())
                .industry(source.getIndustry())
                .documentType(source.getDocumentType())
                .fileUrl(source.getFileUrl())
                .description(source.getDescription())
                .currentVersion(templateVersionPolicy.initialVersion())
                .fileSize(defaultFileSize(source.getFileSize()))
                .tags(copyTags(source.getTags()))
                .createdBy(command.getCreatedBy())
                .build());
        templateCatalogVersionRepository.save(TemplateVersion.builder()
                .template(copied)
                .version(copied.getCurrentVersion())
                .description("复制自模板 #" + source.getId())
                .snapshotName(copied.getName())
                .createdBy(copied.getCreatedBy())
                .build());
        return toDto(copied);
    }

    private TemplateCatalogView toDto(Template template) {
        return templateDtoMapper.toDto(
                template,
                templateCatalogDownloadRecordRepository.countByTemplateId(template.getId()),
                templateCatalogUseRecordRepository.countByTemplateId(template.getId())
        );
    }

    private String toStoredValue(Enum<?> value) {
        if (value == null) {
            return null;
        }
        if (value instanceof com.xiyu.bid.templatecatalog.domain.valueobject.ProductType productType) {
            return productType.getLabel();
        }
        if (value instanceof com.xiyu.bid.templatecatalog.domain.valueobject.IndustryType industryType) {
            return industryType.getLabel();
        }
        if (value instanceof com.xiyu.bid.templatecatalog.domain.valueobject.DocumentType documentType) {
            return documentType.getLabel();
        }
        return value.name();
    }

    private List<String> copyTags(List<String> tags) {
        return tags == null ? List.of() : List.copyOf(tags);
    }

    private String defaultFileSize(String fileSize) {
        return fileSize == null || fileSize.isBlank() ? "未知" : fileSize;
    }

    private void requireValidClassification(TemplateCatalogValidationResult validationResult) {
        if (!validationResult.valid()) {
            throw new IllegalArgumentException(validationResult.message());
        }
    }
}
