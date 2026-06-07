package com.xiyu.bid.templatecatalog.application.service;

import com.xiyu.bid.entity.Template;
import com.xiyu.bid.service.ProjectAccessScopeService;
import com.xiyu.bid.templatecatalog.application.command.TemplateQueryCriteria;
import com.xiyu.bid.templatecatalog.application.mapper.TemplateDtoMapper;
import com.xiyu.bid.templatecatalog.application.view.TemplateCatalogView;
import com.xiyu.bid.templatecatalog.domain.port.TemplateCatalogDownloadRecordRepository;
import com.xiyu.bid.templatecatalog.domain.port.TemplateCatalogRepository;
import com.xiyu.bid.templatecatalog.domain.port.TemplateCatalogUseRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TemplateCatalogQueryAppServiceAccessTest {

    private TemplateCatalogRepository templateCatalogRepository;
    private TemplateCatalogUseRecordRepository templateCatalogUseRecordRepository;
    private TemplateCatalogDownloadRecordRepository templateCatalogDownloadRecordRepository;
    private ProjectAccessScopeService projectAccessScopeService;
    private TemplateCatalogQueryAppService appService;

    @BeforeEach
    void setUp() {
        templateCatalogRepository = mock(TemplateCatalogRepository.class);
        templateCatalogUseRecordRepository = mock(TemplateCatalogUseRecordRepository.class);
        templateCatalogDownloadRecordRepository = mock(TemplateCatalogDownloadRecordRepository.class);
        projectAccessScopeService = mock(ProjectAccessScopeService.class);
        appService = new TemplateCatalogQueryAppService(
                templateCatalogRepository,
                templateCatalogUseRecordRepository,
                templateCatalogDownloadRecordRepository,
                new TemplateDtoMapper(),
                projectAccessScopeService
        );
    }

    @Test
    void list_ShouldCountOnlyCurrentUserVisibleProjectsAndNullProjectRecords() {
        Template template = template(10L);
        when(templateCatalogRepository.findAll(TemplateQueryCriteria.builder().build())).thenReturn(List.of(template));
        when(templateCatalogDownloadRecordRepository.countByTemplateIds(List.of(10L))).thenReturn(Map.of(10L, 4L));
        when(projectAccessScopeService.currentUserHasAdminAccess()).thenReturn(false);
        when(projectAccessScopeService.getAllowedProjectIdsForCurrentUser()).thenReturn(List.of(101L, 102L));
        when(templateCatalogUseRecordRepository.countByTemplateIdsVisibleToProjects(List.of(10L), List.of(101L, 102L)))
                .thenReturn(Map.of(10L, 3L));

        List<TemplateCatalogView> result = appService.list(TemplateQueryCriteria.builder().build());

        assertThat(result).singleElement().extracting(TemplateCatalogView::getUseCount).isEqualTo(3L);
        verify(templateCatalogUseRecordRepository).countByTemplateIdsVisibleToProjects(List.of(10L), List.of(101L, 102L));
    }

    @Test
    void getById_ShouldCountOnlyCurrentUserVisibleProjectsAndNullProjectRecords() {
        Template template = template(10L);
        when(templateCatalogRepository.findById(10L)).thenReturn(Optional.of(template));
        when(templateCatalogDownloadRecordRepository.countByTemplateId(10L)).thenReturn(4L);
        when(projectAccessScopeService.currentUserHasAdminAccess()).thenReturn(false);
        when(projectAccessScopeService.getAllowedProjectIdsForCurrentUser()).thenReturn(List.of(101L));
        when(templateCatalogUseRecordRepository.countByTemplateIdVisibleToProjects(10L, List.of(101L))).thenReturn(2L);

        TemplateCatalogView result = appService.getById(10L);

        assertThat(result.getUseCount()).isEqualTo(2L);
        verify(templateCatalogUseRecordRepository).countByTemplateIdVisibleToProjects(10L, List.of(101L));
    }

    @Test
    void list_ShouldKeepAdminUseCountFullScope() {
        Template template = template(10L);
        when(templateCatalogRepository.findAll(TemplateQueryCriteria.builder().build())).thenReturn(List.of(template));
        when(templateCatalogDownloadRecordRepository.countByTemplateIds(List.of(10L))).thenReturn(Map.of());
        when(projectAccessScopeService.currentUserHasAdminAccess()).thenReturn(true);
        when(templateCatalogUseRecordRepository.countByTemplateIds(List.of(10L))).thenReturn(Map.of(10L, 8L));

        List<TemplateCatalogView> result = appService.list(TemplateQueryCriteria.builder().build());

        assertThat(result).singleElement().extracting(TemplateCatalogView::getUseCount).isEqualTo(8L);
        verify(templateCatalogUseRecordRepository).countByTemplateIds(List.of(10L));
    }

    private Template template(Long id) {
        return Template.builder()
                .id(id)
                .name("商务标模板")
                .category(Template.Category.COMMERCIAL)
                .build();
    }
}
