// Input: DTO、Repository、其他 Service 依赖
// Output: 领域操作结果、事务内状态变更和查询结果
// Pos: Service/业务编排层
// 维护声明: 仅维护本服务职责内的业务规则；跨域变化请同步相关模块.

package com.xiyu.bid.template.service;

import com.xiyu.bid.template.dto.TemplateCopyRequest;
import com.xiyu.bid.template.dto.TemplateDownloadRecordRequest;
import com.xiyu.bid.template.dto.TemplateDTO;
import com.xiyu.bid.template.dto.TemplateUseRecordDTO;
import com.xiyu.bid.template.dto.TemplateUseRecordRequest;
import com.xiyu.bid.template.dto.TemplateVersionDTO;
import com.xiyu.bid.entity.Template;
import com.xiyu.bid.templatecatalog.application.command.TemplateCatalogCopyCommand;
import com.xiyu.bid.templatecatalog.application.command.TemplateCatalogDownloadRecordCommand;
import com.xiyu.bid.templatecatalog.application.command.TemplateCatalogMutationCommand;
import com.xiyu.bid.templatecatalog.application.command.TemplateQueryCriteria;
import com.xiyu.bid.templatecatalog.application.command.TemplateCatalogUseRecordCommand;
import com.xiyu.bid.templatecatalog.application.service.TemplateCatalogActivityAppService;
import com.xiyu.bid.templatecatalog.application.service.TemplateCatalogCommandAppService;
import com.xiyu.bid.templatecatalog.application.service.TemplateCatalogQueryAppService;
import com.xiyu.bid.templatecatalog.application.service.TemplateCatalogVersionAppService;
import com.xiyu.bid.templatecatalog.application.view.TemplateCatalogUseRecordView;
import com.xiyu.bid.templatecatalog.application.view.TemplateCatalogVersionView;
import com.xiyu.bid.templatecatalog.application.view.TemplateCatalogView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class TemplateService {

    private final TemplateCatalogCommandAppService templateCatalogCommandAppService;
    private final TemplateCatalogQueryAppService templateCatalogQueryAppService;
    private final TemplateCatalogVersionAppService templateCatalogVersionAppService;
    private final TemplateCatalogActivityAppService templateCatalogActivityAppService;

    @Transactional
    public TemplateDTO createTemplate(TemplateDTO dto) {
        log.info("Creating template: {}", dto.getName());
        return toDto(templateCatalogCommandAppService.create(toMutationCommand(dto)));
    }

    @Transactional(readOnly = true)
    public List<TemplateDTO> getAllTemplates(TemplateQueryCriteria criteria) {
        return templateCatalogQueryAppService.list(criteria).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public TemplateDTO getTemplateById(Long id) {
        return toDto(templateCatalogQueryAppService.getById(id));
    }

    @Transactional
    public TemplateDTO updateTemplate(Long id, TemplateDTO dto) {
        log.info("Updating template: {}", id);
        return toDto(templateCatalogCommandAppService.update(id, toMutationCommand(dto)));
    }

    @Transactional
    public void deleteTemplate(Long id) {
        log.info("Deleting template: {}", id);
        templateCatalogCommandAppService.delete(id);
    }

    @Transactional(readOnly = true)
    public List<TemplateDTO> getTemplatesByCategory(Template.Category category) {
        return templateCatalogQueryAppService.getByCategory(category).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TemplateDTO> getTemplatesByCreatedBy(Long createdBy) {
        return templateCatalogQueryAppService.getByCreator(createdBy).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public TemplateDTO copyTemplate(Long id, TemplateCopyRequest request) {
        return toDto(templateCatalogCommandAppService.copy(id, TemplateCatalogCopyCommand.builder()
                .name(request.getName())
                .createdBy(request.getCreatedBy())
                .build()));
    }

    @Transactional
    public List<TemplateVersionDTO> getTemplateVersions(Long id) {
        return templateCatalogVersionAppService.list(id).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public TemplateUseRecordDTO createTemplateUseRecord(Long id, TemplateUseRecordRequest request) {
        return toDto(templateCatalogActivityAppService.createUseRecord(id, TemplateCatalogUseRecordCommand.builder()
                .documentName(request.getDocumentName())
                .docType(request.getDocType())
                .projectId(request.getProjectId())
                .applyOptions(request.getApplyOptions())
                .usedBy(request.getUsedBy())
                .build()));
    }

    @Transactional
    public TemplateDTO createTemplateDownloadRecord(Long id, TemplateDownloadRecordRequest request) {
        return toDto(templateCatalogActivityAppService.createDownloadRecord(id, TemplateCatalogDownloadRecordCommand.builder()
                .downloadedBy(request != null ? request.getDownloadedBy() : null)
                .build()));
    }

    private TemplateCatalogMutationCommand toMutationCommand(TemplateDTO dto) {
        return TemplateCatalogMutationCommand.builder()
                .name(dto.getName())
                .category(dto.getCategory())
                .productType(dto.getProductType())
                .industry(dto.getIndustry())
                .documentType(dto.getDocumentType())
                .fileUrl(dto.getFileUrl())
                .description(dto.getDescription())
                .fileSize(dto.getFileSize())
                .tags(dto.getTags())
                .createdBy(dto.getCreatedBy())
                .build();
    }

    private TemplateDTO toDto(TemplateCatalogView view) {
        return TemplateDTO.builder()
                .id(view.getId())
                .name(view.getName())
                .category(view.getCategory())
                .productType(view.getProductType())
                .industry(view.getIndustry())
                .documentType(view.getDocumentType())
                .fileUrl(view.getFileUrl())
                .description(view.getDescription())
                .currentVersion(view.getCurrentVersion())
                .fileSize(view.getFileSize())
                .downloads(view.getDownloads())
                .useCount(view.getUseCount())
                .tags(view.getTags())
                .createdBy(view.getCreatedBy())
                .createdAt(view.getCreatedAt())
                .updatedAt(view.getUpdatedAt())
                .build();
    }

    private TemplateVersionDTO toDto(TemplateCatalogVersionView view) {
        return TemplateVersionDTO.builder()
                .id(view.getId())
                .version(view.getVersion())
                .description(view.getDescription())
                .snapshotName(view.getSnapshotName())
                .createdBy(view.getCreatedBy())
                .createdAt(view.getCreatedAt())
                .build();
    }

    private TemplateUseRecordDTO toDto(TemplateCatalogUseRecordView view) {
        return TemplateUseRecordDTO.builder()
                .id(view.getId())
                .documentName(view.getDocumentName())
                .docType(view.getDocType())
                .projectId(view.getProjectId())
                .applyOptions(view.getApplyOptions())
                .usedBy(view.getUsedBy())
                .usedAt(view.getUsedAt())
                .build();
    }
}
