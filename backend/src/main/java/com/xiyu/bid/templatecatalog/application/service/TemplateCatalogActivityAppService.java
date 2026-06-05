// Input: template id, activity command, current-user project scope
// Output: template use/download activity views with project-scoped use counts
// Pos: Application Service/应用编排层
// 维护声明: 使用记录写入前必须复用 ProjectAccessScopeService 做项目访问断言。
package com.xiyu.bid.templatecatalog.application.service;

import com.xiyu.bid.entity.Template;
import com.xiyu.bid.entity.TemplateDownloadRecord;
import com.xiyu.bid.entity.TemplateUseRecord;
import com.xiyu.bid.exception.ResourceNotFoundException;
import com.xiyu.bid.service.ProjectAccessScopeService;
import com.xiyu.bid.templatecatalog.application.command.TemplateCatalogDownloadRecordCommand;
import com.xiyu.bid.templatecatalog.application.command.TemplateCatalogUseRecordCommand;
import com.xiyu.bid.templatecatalog.application.mapper.TemplateDtoMapper;
import com.xiyu.bid.templatecatalog.application.mapper.TemplateUseRecordDtoMapper;
import com.xiyu.bid.templatecatalog.application.view.TemplateCatalogUseRecordView;
import com.xiyu.bid.templatecatalog.application.view.TemplateCatalogView;
import com.xiyu.bid.templatecatalog.domain.port.TemplateCatalogDownloadRecordRepository;
import com.xiyu.bid.templatecatalog.domain.port.TemplateCatalogRepository;
import com.xiyu.bid.templatecatalog.domain.port.TemplateCatalogUseRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TemplateCatalogActivityAppService {

    private final TemplateCatalogRepository templateCatalogRepository;
    private final TemplateCatalogUseRecordRepository templateCatalogUseRecordRepository;
    private final TemplateCatalogDownloadRecordRepository templateCatalogDownloadRecordRepository;
    private final TemplateDtoMapper templateDtoMapper;
    private final TemplateUseRecordDtoMapper templateUseRecordDtoMapper;
    private final TemplateVersionBootstrapper templateVersionBootstrapper;
    private final ProjectAccessScopeService projectAccessScopeService;

    @Transactional
    public TemplateCatalogUseRecordView createUseRecord(Long id, TemplateCatalogUseRecordCommand command) {
        Template template = templateCatalogRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Template", id.toString()));
        templateVersionBootstrapper.ensureInitialized(template);
        assertCanCreateUseRecord(command.getProjectId());

        TemplateUseRecord record = templateCatalogUseRecordRepository.save(TemplateUseRecord.builder()
                .template(template)
                .documentName(command.getDocumentName())
                .docType(command.getDocType())
                .projectId(command.getProjectId())
                .appliedOptions(joinOptions(command.getApplyOptions()))
                .usedBy(command.getUsedBy())
                .build());
        return templateUseRecordDtoMapper.toDto(record);
    }

    @Transactional
    public TemplateCatalogView createDownloadRecord(Long id, TemplateCatalogDownloadRecordCommand command) {
        Template template = templateCatalogRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Template", id.toString()));
        templateVersionBootstrapper.ensureInitialized(template);

        templateCatalogDownloadRecordRepository.save(TemplateDownloadRecord.builder()
                .template(template)
                .downloadedBy(command != null ? command.getDownloadedBy() : null)
                .build());
        return templateDtoMapper.toDto(
                template,
                templateCatalogDownloadRecordRepository.countByTemplateId(template.getId()),
                countVisibleUseRecords(template.getId())
        );
    }

    private String joinOptions(List<String> options) {
        return (options == null || options.isEmpty()) ? "" : String.join(",", options);
    }

    private void assertCanCreateUseRecord(Long projectId) {
        if (projectId != null) {
            projectAccessScopeService.assertCurrentUserCanAccessProject(projectId);
        }
    }

    private long countVisibleUseRecords(Long templateId) {
        if (projectAccessScopeService.currentUserHasAdminAccess()) {
            return templateCatalogUseRecordRepository.countByTemplateId(templateId);
        }
        return templateCatalogUseRecordRepository.countByTemplateIdVisibleToProjects(
                templateId,
                projectAccessScopeService.getAllowedProjectIdsForCurrentUser()
        );
    }
}
