// Input: ProjectImportService.importProject 行为
// Output: Mockito 单元测试覆盖"历史项目导入后创建项目档案"场景
// Pos: backend test source - 蓝图 §4.1.1.1.1 修复回归
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.project.service;

import com.xiyu.bid.casework.application.ProjectArchiveWorkflowService;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.project.dto.ProjectImportRequest;
import com.xiyu.bid.repository.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectImportServiceTest {

    @Mock private ProjectRepository projectRepository;
    @Mock private ProjectArchiveWorkflowService projectArchiveWorkflowService;

    private ProjectImportService service;

    @BeforeEach
    void setUp() {
        service = new ProjectImportService(projectRepository, projectArchiveWorkflowService);
    }

    @Test
    void importProject_shouldCreateArchiveForHistoricalProject() {
        when(projectRepository.save(any(Project.class))).thenAnswer(inv -> {
            Project p = inv.getArgument(0);
            p.setId(200L);
            return p;
        });

        ProjectImportRequest request = ProjectImportRequest.builder()
                .name("历史项目")
                .tenderId(88001L)
                .managerId(2L)
                .build();

        service.importProject(request);

        verify(projectArchiveWorkflowService, times(1))
                .createArchive(200L, "历史项目", "ACTIVE");
    }
}
