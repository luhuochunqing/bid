// Input: ProjectService 创建/详情链路的 customFields 行为
// Output: CO-601 US1 — customFields 落库 projects.custom_fields / 详情返回 Map / 未知 scope 过滤 / NULL 列降级空 Map
// Pos: backend test source
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.project.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.demo.service.DemoDataProvider;
import com.xiyu.bid.demo.service.DemoFusionService;
import com.xiyu.bid.demo.service.DemoModeService;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.project.dto.ProjectDTO;
import com.xiyu.bid.project.repository.ProjectLeadAssignmentRepository;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.service.ProjectAccessScopeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectServiceCustomFieldsTest {

    private static final ObjectMapper OM = new ObjectMapper();

    @Mock ProjectRepository projectRepository;
    @Mock ProjectAccessScopeService projectAccessScopeService;
    @Mock DemoModeService demoModeService;
    @Mock DemoDataProvider demoDataProvider;
    @Mock DemoFusionService demoFusionService;
    @Mock ProjectImportService projectImportService;
    @Mock ProjectQueryService projectQueryService;
    @Mock ProjectLeadAssignmentRepository projectLeadAssignmentRepository;

    ProjectService service;

    @BeforeEach
    void setUp() {
        service = new ProjectService(projectRepository, projectAccessScopeService, demoModeService,
                demoDataProvider, demoFusionService, projectImportService, projectQueryService,
                projectLeadAssignmentRepository, new CustomFieldsCodec(new ObjectMapper()));
        lenient().when(demoModeService.isEnabled()).thenReturn(false);
    }

    @Test
    void createProject_persistsCustomFieldsAsJsonColumn_andReturnsMap() throws Exception {
        Map<String, Object> customFields = new LinkedHashMap<>();
        customFields.put("project.basic", Map.of("budgetLevel", "重点客户"));
        customFields.put("project.detail", Map.of("siteVisitDone", true));
        ProjectDTO req = ProjectDTO.builder()
                .name("项目A").tenderId(7L).managerId(9L)
                .customFields(customFields)
                .build();
        when(projectRepository.findByTenderId(7L)).thenReturn(List.of());
        when(projectRepository.save(any(Project.class))).thenAnswer(inv -> {
            Project p = inv.getArgument(0);
            p.setId(100L);
            return p;
        });

        ProjectDTO result = service.createProject(req);

        ArgumentCaptor<Project> captor = ArgumentCaptor.forClass(Project.class);
        verify(projectRepository).save(captor.capture());
        // 持久化列为 JSON String；解析回 Map 比较（避免 key 顺序敏感）
        Map<String, Object> stored = OM.readValue(captor.getValue().getCustomFields(), Map.class);
        assertThat(stored).isEqualTo(customFields);
        // 创建响应原样返回已存值（Map 形态，供前端回显）
        assertThat(result.getCustomFields()).isEqualTo(customFields);
    }

    @Test
    void createProject_filtersUnknownScopeKeys() throws Exception {
        Map<String, Object> customFields = new LinkedHashMap<>();
        customFields.put("project.basic", Map.of("budgetLevel", "重点客户"));
        customFields.put("evil.scope", Map.of("x", 1));
        ProjectDTO req = ProjectDTO.builder()
                .name("项目A").tenderId(7L).managerId(9L)
                .customFields(customFields)
                .build();
        when(projectRepository.findByTenderId(7L)).thenReturn(List.of());
        when(projectRepository.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));

        service.createProject(req);

        ArgumentCaptor<Project> captor = ArgumentCaptor.forClass(Project.class);
        verify(projectRepository).save(captor.capture());
        Map<String, Object> stored = OM.readValue(captor.getValue().getCustomFields(), Map.class);
        // 未知 scope 键被过滤丢弃（log.warn 由 CustomFieldsCodec.filterScopes 输出），不阻断主流程
        assertThat(stored).isEqualTo(Map.of("project.basic", Map.of("budgetLevel", "重点客户")));
    }

    @Test
    void getProjectById_returnsCustomFieldsAsMap() {
        Project entity = Project.builder()
                .id(1L).name("项目A").managerId(9L)
                .customFields("{\"project.basic\":{\"budgetLevel\":\"重点客户\"}}")
                .build();
        doNothing().when(projectAccessScopeService).assertCurrentUserCanAccessProject(1L);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(projectLeadAssignmentRepository.findByProjectId(1L)).thenReturn(Optional.empty());

        ProjectDTO dto = service.getProjectById(1L);

        assertThat(dto.getCustomFields())
                .isEqualTo(Map.of("project.basic", Map.of("budgetLevel", "重点客户")));
    }

    @Test
    void getProjectById_nullColumn_returnsEmptyMap() {
        // 老数据 custom_fields 列为 NULL → 返回空 Map（契约 §3：减少前端判空）
        Project entity = Project.builder()
                .id(1L).name("老项目").managerId(9L)
                .customFields(null)
                .build();
        doNothing().when(projectAccessScopeService).assertCurrentUserCanAccessProject(1L);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(projectLeadAssignmentRepository.findByProjectId(1L)).thenReturn(Optional.empty());

        ProjectDTO dto = service.getProjectById(1L);

        assertThat(dto.getCustomFields()).isNotNull().isEmpty();
    }
}
