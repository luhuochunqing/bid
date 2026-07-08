// Input: ProjectExportService 导出逻辑
// Output: 验证投标状态中文显示与辅助人员列正确导出
// Pos: backend test source
package com.xiyu.bid.project.service;

import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.project.entity.ProjectInitiationDetails;
import com.xiyu.bid.project.entity.ProjectLeadAssignment;
import com.xiyu.bid.project.repository.ProjectInitiationDetailsRepository;
import com.xiyu.bid.project.repository.ProjectLeadAssignmentRepository;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.service.ProjectAccessScopeService;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectExportServiceTest {

    @Mock ProjectRepository projectRepository;
    @Mock ProjectInitiationDetailsRepository initiationDetailsRepository;
    @Mock ProjectLeadAssignmentRepository projectLeadAssignmentRepository;
    @Mock TenderRepository tenderRepository;
    @Mock ProjectAccessScopeService projectAccessScopeService;
    @Mock UserRepository userRepository;

    ProjectExportService service;

    @BeforeEach
    void setUp() {
        service = new ProjectExportService(projectRepository, initiationDetailsRepository,
                projectLeadAssignmentRepository, tenderRepository, projectAccessScopeService, userRepository);
        lenient().when(projectAccessScopeService.getAllowedProjectIdsForCurrentUser())
                .thenReturn(Collections.emptyList());
        lenient().when(tenderRepository.findAllById(any())).thenReturn(Collections.emptyList());
    }

    @Test
    void exportProjectsAsExcel_shouldContainAssistantColumnAndLocalizedBidStatus() throws IOException {
        // Given
        Project project = Project.builder()
                .id(1L)
                .name("测试项目")
                .tenderId(2L)
                .status(Project.Status.PENDING_INITIATION)
                .stage("INITIATED")
                .managerId(10L)
                .createdAt(LocalDateTime.of(2026, 7, 7, 10, 0))
                .build();

        ProjectInitiationDetails details = ProjectInitiationDetails.builder()
                .projectId(1L)
                .ownerUnit("测试业主")
                .bidStatus("PENDING_INITIATION")
                .projectLeaderName("张三")
                .biddingLeaderName("李四")
                .build();

        ProjectLeadAssignment assignment = ProjectLeadAssignment.builder()
                .projectId(1L)
                .primaryLeadUserId(20L)
                .secondaryLeadUserId(30L)
                .build();

        User assistant = User.builder()
                .id(30L)
                .fullName("王五")
                .username("wangwu")
                .email("wangwu@example.com")
                .build();

        when(projectRepository.findAll()).thenReturn(List.of(project));
        when(initiationDetailsRepository.findByProjectIdIn(List.of(1L)))
                .thenReturn(List.of(details));
        when(projectLeadAssignmentRepository.findByProjectIdIn(List.of(1L)))
                .thenReturn(List.of(assignment));
        when(userRepository.findByIdIn(List.of(30L))).thenReturn(List.of(assistant));

        // When
        ProjectExportService.ExportResult result = service.exportProjectsAsExcel(
                null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null);

        // Then
        XSSFWorkbook workbook = new XSSFWorkbook(result.data());
        var sheet = workbook.getSheetAt(0);
        var headerRow = sheet.getRow(0);

        int assistantCol = findColumnIndex(headerRow, "辅助人员");
        int bidStatusCol = findColumnIndex(headerRow, "投标状态");

        assertThat(assistantCol).as("表头应包含辅助人员列").isGreaterThanOrEqualTo(0);
        assertThat(bidStatusCol).as("表头应包含投标状态列").isGreaterThanOrEqualTo(0);

        var dataRow = sheet.getRow(1);
        assertThat(dataRow.getCell(bidStatusCol).getStringCellValue())
                .as("投标状态应显示为中文")
                .isEqualTo("待立项");
        assertThat(dataRow.getCell(assistantCol).getStringCellValue())
                .as("辅助人员应显示用户姓名")
                .isEqualTo("王五");

        workbook.close();
    }

    @Test
    void exportProjectsAsExcel_shouldKeepRawValueWhenBidStatusUnknown() throws IOException {
        Project project = Project.builder()
                .id(2L)
                .name("异常状态项目")
                .tenderId(3L)
                .status(Project.Status.INITIATED)
                .stage("INITIATED")
                .managerId(10L)
                .createdAt(LocalDateTime.of(2026, 7, 7, 10, 0))
                .build();

        ProjectInitiationDetails details = ProjectInitiationDetails.builder()
                .projectId(2L)
                .bidStatus("UNKNOWN_STATUS")
                .build();

        when(projectRepository.findAll()).thenReturn(List.of(project));
        when(initiationDetailsRepository.findByProjectIdIn(List.of(2L)))
                .thenReturn(List.of(details));
        when(projectLeadAssignmentRepository.findByProjectIdIn(List.of(2L)))
                .thenReturn(Collections.emptyList());

        ProjectExportService.ExportResult result = service.exportProjectsAsExcel(
                null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null);

        XSSFWorkbook workbook = new XSSFWorkbook(result.data());
        var sheet = workbook.getSheetAt(0);
        int bidStatusCol = findColumnIndex(sheet.getRow(0), "投标状态");

        assertThat(sheet.getRow(1).getCell(bidStatusCol).getStringCellValue())
                .as("非法投标状态应保留原值")
                .isEqualTo("UNKNOWN_STATUS");

        workbook.close();
    }

    @Test
    void exportProjectsAsExcel_shouldLeaveAssistantBlankWhenNotAssigned() throws IOException {
        Project project = Project.builder()
                .id(3L)
                .name("无辅助人员项目")
                .tenderId(4L)
                .status(Project.Status.BIDDING)
                .stage("DRAFTING")
                .managerId(10L)
                .createdAt(LocalDateTime.of(2026, 7, 7, 10, 0))
                .build();

        ProjectInitiationDetails details = ProjectInitiationDetails.builder()
                .projectId(3L)
                .bidStatus("BIDDING")
                .build();

        ProjectLeadAssignment assignment = ProjectLeadAssignment.builder()
                .projectId(3L)
                .primaryLeadUserId(20L)
                .build();

        when(projectRepository.findAll()).thenReturn(List.of(project));
        when(initiationDetailsRepository.findByProjectIdIn(List.of(3L)))
                .thenReturn(List.of(details));
        when(projectLeadAssignmentRepository.findByProjectIdIn(List.of(3L)))
                .thenReturn(List.of(assignment));

        ProjectExportService.ExportResult result = service.exportProjectsAsExcel(
                null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null);

        XSSFWorkbook workbook = new XSSFWorkbook(result.data());
        var sheet = workbook.getSheetAt(0);
        int assistantCol = findColumnIndex(sheet.getRow(0), "辅助人员");
        int bidStatusCol = findColumnIndex(sheet.getRow(0), "投标状态");

        assertThat(sheet.getRow(1).getCell(bidStatusCol).getStringCellValue())
                .isEqualTo("投标中");
        assertThat(sheet.getRow(1).getCell(assistantCol).getStringCellValue())
                .as("未分配辅助人员时应为空")
                .isEmpty();

        workbook.close();
    }

    private static int findColumnIndex(org.apache.poi.ss.usermodel.Row headerRow, String label) {
        for (var cell : headerRow) {
            if (label.equals(cell.getStringCellValue())) {
                return cell.getColumnIndex();
            }
        }
        return -1;
    }
}
