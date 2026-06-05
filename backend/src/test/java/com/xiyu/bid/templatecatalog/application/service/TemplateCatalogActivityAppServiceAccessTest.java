package com.xiyu.bid.templatecatalog.application.service;

import com.xiyu.bid.entity.Template;
import com.xiyu.bid.entity.TemplateDownloadRecord;
import com.xiyu.bid.entity.TemplateUseRecord;
import com.xiyu.bid.service.ProjectAccessScopeService;
import com.xiyu.bid.templatecatalog.application.command.TemplateCatalogDownloadRecordCommand;
import com.xiyu.bid.templatecatalog.application.command.TemplateCatalogUseRecordCommand;
import com.xiyu.bid.templatecatalog.application.mapper.TemplateDtoMapper;
import com.xiyu.bid.templatecatalog.application.mapper.TemplateUseRecordDtoMapper;
import com.xiyu.bid.templatecatalog.application.view.TemplateCatalogView;
import com.xiyu.bid.templatecatalog.domain.port.TemplateCatalogDownloadRecordRepository;
import com.xiyu.bid.templatecatalog.domain.port.TemplateCatalogRepository;
import com.xiyu.bid.templatecatalog.domain.port.TemplateCatalogUseRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TemplateCatalogActivityAppServiceAccessTest {

    private TemplateCatalogRepository templateCatalogRepository;
    private TemplateCatalogUseRecordRepository templateCatalogUseRecordRepository;
    private TemplateCatalogDownloadRecordRepository templateCatalogDownloadRecordRepository;
    private ProjectAccessScopeService projectAccessScopeService;
    private TemplateCatalogActivityAppService appService;

    @BeforeEach
    void setUp() {
        templateCatalogRepository = mock(TemplateCatalogRepository.class);
        templateCatalogUseRecordRepository = mock(TemplateCatalogUseRecordRepository.class);
        templateCatalogDownloadRecordRepository = mock(TemplateCatalogDownloadRecordRepository.class);
        projectAccessScopeService = mock(ProjectAccessScopeService.class);
        TemplateVersionBootstrapper templateVersionBootstrapper = mock(TemplateVersionBootstrapper.class);
        Template template = Template.builder().id(9L).name("技术标模板").category(Template.Category.TECHNICAL).build();

        when(templateCatalogRepository.findById(9L)).thenReturn(Optional.of(template));
        when(templateVersionBootstrapper.ensureInitialized(template)).thenReturn(template);
        when(templateCatalogUseRecordRepository.save(any(TemplateUseRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(templateCatalogDownloadRecordRepository.save(any(TemplateDownloadRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        appService = new TemplateCatalogActivityAppService(
                templateCatalogRepository,
                templateCatalogUseRecordRepository,
                templateCatalogDownloadRecordRepository,
                new TemplateDtoMapper(),
                new TemplateUseRecordDtoMapper(),
                templateVersionBootstrapper,
                projectAccessScopeService
        );
    }

    @Test
    void createUseRecord_ShouldRejectInvisibleProjectId() {
        doThrow(new AccessDeniedException("权限不足，无法访问该项目"))
                .when(projectAccessScopeService).assertCurrentUserCanAccessProject(200L);

        TemplateCatalogUseRecordCommand command = commandWithProject(200L);

        assertThatThrownBy(() -> appService.createUseRecord(9L, command))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("权限不足");

        verify(templateCatalogUseRecordRepository, never()).save(any());
    }

    @Test
    void createUseRecord_ShouldAllowVisibleProjectId() {
        assertThatCode(() -> appService.createUseRecord(9L, commandWithProject(100L)))
                .doesNotThrowAnyException();

        verify(projectAccessScopeService).assertCurrentUserCanAccessProject(100L);
        verify(templateCatalogUseRecordRepository).save(any(TemplateUseRecord.class));
    }

    @Test
    void createUseRecord_ShouldAllowNullProjectId() {
        assertThatCode(() -> appService.createUseRecord(9L, commandWithProject(null)))
                .doesNotThrowAnyException();

        verify(projectAccessScopeService, never()).assertCurrentUserCanAccessProject(any());
        verify(templateCatalogUseRecordRepository).save(any(TemplateUseRecord.class));
    }

    @Test
    void createDownloadRecord_ShouldReturnProjectScopedUseCountForNonAdmin() {
        when(projectAccessScopeService.currentUserHasAdminAccess()).thenReturn(false);
        when(projectAccessScopeService.getAllowedProjectIdsForCurrentUser()).thenReturn(List.of(100L));
        when(templateCatalogDownloadRecordRepository.countByTemplateId(9L)).thenReturn(5L);
        when(templateCatalogUseRecordRepository.countByTemplateId(9L)).thenReturn(9L);
        when(templateCatalogUseRecordRepository.countByTemplateIdVisibleToProjects(9L, List.of(100L)))
                .thenReturn(2L);

        TemplateCatalogView result = appService.createDownloadRecord(
                9L,
                TemplateCatalogDownloadRecordCommand.builder().downloadedBy(7L).build()
        );

        assertThat(result.getDownloads()).isEqualTo(5L);
        assertThat(result.getUseCount()).isEqualTo(2L);
        verify(templateCatalogUseRecordRepository, never()).countByTemplateId(9L);
    }

    private TemplateCatalogUseRecordCommand commandWithProject(Long projectId) {
        return TemplateCatalogUseRecordCommand.builder()
                .documentName("投标文件")
                .docType("TECHNICAL")
                .projectId(projectId)
                .applyOptions(List.of("目录"))
                .usedBy(7L)
                .build();
    }
}
