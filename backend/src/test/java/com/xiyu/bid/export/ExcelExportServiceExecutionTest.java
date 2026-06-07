package com.xiyu.bid.export;

import com.xiyu.bid.audit.service.AuditLogService;
import com.xiyu.bid.entity.Case;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.Qualification;
import com.xiyu.bid.entity.Template;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.export.service.ExcelExportService;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExcelExportServiceExecutionTest extends AbstractExcelExportServiceTest {

    @Test
    void exportToExcel_WithTendersType_ShouldExportTenders() throws Exception {
        Page<Tender> page = new PageImpl<>(List.of(testTender), PageRequest.of(0, 1000), 1);
        when(tenderRepository.findAll(any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(page)
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(1, 1000), 1));
        Path outputPath = Path.of("/tmp/test_tenders.xlsx");

        long fileSize = excelExportService.exportToExcel("tenders", outputPath, null, 1L);

        assertThat(fileSize).isGreaterThan(0);
        verify(tenderRepository, atLeastOnce()).findAll(any(org.springframework.data.domain.Pageable.class));
        deleteIfExists(outputPath);
    }

    @Test
    void exportToExcelWithResult_ShouldReturnGeneratedFileSizeAndRecordCount() throws Exception {
        Page<Tender> page = new PageImpl<>(List.of(testTender), PageRequest.of(0, 1000), 1);
        when(tenderRepository.findAll(any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(page)
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(1, 1000), 1));
        Path outputPath = Path.of("/tmp/test_tenders_result.xlsx");

        ExcelExportService.ExportFileResult result = excelExportService.exportToExcelWithResult(
                "tenders", outputPath, null, 1L);

        assertThat(result.fileSize()).isGreaterThan(0);
        assertThat(result.recordCount()).isEqualTo(1);
        assertThat(java.nio.file.Files.exists(outputPath)).isTrue();
        deleteIfExists(outputPath);
    }

    @Test
    void exportToExcel_WithProjectsType_ShouldExportProjects() throws Exception {
        Page<Project> page = new PageImpl<>(List.of(testProject), PageRequest.of(0, 1000), 1);
        when(projectRepository.findAll(any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(page)
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(1, 1000), 1));
        Path outputPath = Path.of("/tmp/test_projects.xlsx");

        long fileSize = excelExportService.exportToExcel("projects", outputPath, null, 1L);

        assertThat(fileSize).isGreaterThan(0);
        verify(projectRepository, atLeastOnce()).findAll(any(org.springframework.data.domain.Pageable.class));
        deleteIfExists(outputPath);
    }

    @Test
    void exportToExcel_WithProjectsType_ShouldOnlyExportAccessibleProjects() throws Exception {
        Project hiddenProject = Project.builder()
                .id(2L)
                .name("隐藏项目")
                .status(Project.Status.BIDDING)
                .managerId(2L)
                .build();
        Page<Project> page = new PageImpl<>(List.of(testProject, hiddenProject), PageRequest.of(0, 1000), 2);
        when(projectAccessScopeService.currentUserHasAdminAccess()).thenReturn(false);
        when(projectAccessScopeService.filterAccessibleProjects(List.of(testProject, hiddenProject)))
                .thenReturn(List.of(testProject));
        when(projectRepository.findAll()).thenReturn(List.of(testProject, hiddenProject));
        when(projectRepository.findAll(any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(page)
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(1, 1000), 2));
        Path outputPath = Path.of("/tmp/test_visible_projects.xlsx");

        excelExportService.exportToExcel("projects", outputPath, null, 1L);

        try (XSSFWorkbook workbook = new XSSFWorkbook(java.nio.file.Files.newInputStream(outputPath))) {
            assertThat(workbook.getSheetAt(0).getLastRowNum()).isEqualTo(1);
            assertThat(workbook.getSheetAt(0).getRow(1).getCell(1).getStringCellValue())
                    .isEqualTo(testProject.getName());
        }
        deleteIfExists(outputPath);
    }

    @Test
    void exportToExcel_WithTendersType_ShouldOnlyExportAccessibleLinkedTenders() throws Exception {
        Tender hiddenTender = Tender.builder()
                .id(2L)
                .title("隐藏标讯")
                .status(Tender.Status.TRACKING)
                .build();
        Project hiddenProject = Project.builder()
                .id(2L)
                .name("隐藏项目")
                .tenderId(hiddenTender.getId())
                .status(Project.Status.BIDDING)
                .managerId(2L)
                .build();
        Page<Tender> page = new PageImpl<>(List.of(testTender, hiddenTender), PageRequest.of(0, 1000), 2);
        when(projectAccessScopeService.currentUserHasAdminAccess()).thenReturn(false);
        when(projectAccessScopeService.filterAccessibleProjects(List.of(testProject, hiddenProject)))
                .thenReturn(List.of(testProject));
        when(projectRepository.findAll()).thenReturn(List.of(testProject, hiddenProject));
        when(tenderRepository.findAll(any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(page)
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(1, 1000), 2));
        Path outputPath = Path.of("/tmp/test_visible_tenders.xlsx");

        excelExportService.exportToExcel("tenders", outputPath, null, 1L);

        try (XSSFWorkbook workbook = new XSSFWorkbook(java.nio.file.Files.newInputStream(outputPath))) {
            assertThat(workbook.getSheetAt(0).getLastRowNum()).isEqualTo(1);
            assertThat(workbook.getSheetAt(0).getRow(1).getCell(1).getStringCellValue())
                    .isEqualTo(testTender.getTitle());
        }
        deleteIfExists(outputPath);
    }

    @Test
    void exportToExcel_WithTendersType_ShouldNotExportUnlinkedTendersForNonAdmin() throws Exception {
        Tender unlinkedTender = Tender.builder()
                .id(3L)
                .title("未关联项目标讯")
                .status(Tender.Status.PENDING_ASSIGNMENT)
                .build();
        Page<Tender> page = new PageImpl<>(List.of(testTender, unlinkedTender), PageRequest.of(0, 1000), 2);
        when(projectAccessScopeService.currentUserHasAdminAccess()).thenReturn(false);
        when(projectAccessScopeService.filterAccessibleProjects(List.of(testProject)))
                .thenReturn(List.of(testProject));
        when(projectRepository.findAll()).thenReturn(List.of(testProject));
        when(tenderRepository.findAll(any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(page)
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(1, 1000), 2));
        Path outputPath = Path.of("/tmp/test_no_unlinked_tenders.xlsx");

        excelExportService.exportToExcel("tenders", outputPath, null, 1L);

        try (XSSFWorkbook workbook = new XSSFWorkbook(java.nio.file.Files.newInputStream(outputPath))) {
            assertThat(workbook.getSheetAt(0).getLastRowNum()).isEqualTo(1);
            assertThat(workbook.getSheetAt(0).getRow(1).getCell(1).getStringCellValue())
                    .isEqualTo(testTender.getTitle());
        }
        deleteIfExists(outputPath);
    }

    @Test
    void exportToExcel_WithQualificationsType_ShouldExportQualifications() throws Exception {
        Page<Qualification> page = new PageImpl<>(List.of(testQualification), PageRequest.of(0, 1000), 1);
        when(qualificationRepository.findAll(any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(page)
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(1, 1000), 1));
        Path outputPath = Path.of("/tmp/test_qualifications.xlsx");

        long fileSize = excelExportService.exportToExcel("qualifications", outputPath, null, 1L);

        assertThat(fileSize).isGreaterThan(0);
        verify(qualificationRepository, atLeastOnce()).findAll(any(org.springframework.data.domain.Pageable.class));
        deleteIfExists(outputPath);
    }

    @Test
    void exportToExcel_WithCasesType_ShouldExportCases() throws Exception {
        Page<Case> page = new PageImpl<>(List.of(testCase), PageRequest.of(0, 1000), 1);
        when(caseRepository.findAll(any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(page)
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(1, 1000), 1));
        Path outputPath = Path.of("/tmp/test_cases.xlsx");

        long fileSize = excelExportService.exportToExcel("cases", outputPath, null, 1L);

        assertThat(fileSize).isGreaterThan(0);
        verify(caseRepository, atLeastOnce()).findAll(any(org.springframework.data.domain.Pageable.class));
        deleteIfExists(outputPath);
    }

    @Test
    void exportToExcel_WithTemplatesType_ShouldExportTemplates() throws Exception {
        Page<Template> page = new PageImpl<>(List.of(testTemplate), PageRequest.of(0, 1000), 1);
        when(templateRepository.findAll(any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(page)
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(1, 1000), 1));
        Path outputPath = Path.of("/tmp/test_templates.xlsx");

        long fileSize = excelExportService.exportToExcel("templates", outputPath, null, 1L);

        assertThat(fileSize).isGreaterThan(0);
        verify(templateRepository, atLeastOnce()).findAll(any(org.springframework.data.domain.Pageable.class));
        deleteIfExists(outputPath);
    }

    @Test
    void exportToExcel_WithEmptyList_ShouldThrowNoDataException() {
        Page<Tender> emptyPage = new PageImpl<>(List.of(), PageRequest.of(0, 1000), 0);
        when(tenderRepository.findAll(any(org.springframework.data.domain.Pageable.class))).thenReturn(emptyPage);
        Path outputPath = Path.of("/tmp/test_empty_tenders.xlsx");

        assertThatThrownBy(() -> excelExportService.exportToExcel("tenders", outputPath, null, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("无数据");
    }

    @Test
    void exportToExcel_WithInvalidType_ShouldThrowException() {
        assertThatThrownBy(() -> excelExportService.exportToExcel("invalid_type", Path.of("/tmp/test_invalid.xlsx"), null, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported export type");
    }

    @Test
    void exportToExcel_WithNullType_ShouldThrowException() {
        assertThatThrownBy(() -> excelExportService.exportToExcel(null, Path.of("/tmp/test_null.xlsx"), null, 1L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void exportToExcel_WithPathTraversal_ShouldThrowException() {
        assertThatThrownBy(() -> excelExportService.exportToExcel("tenders", Path.of("/tmp/test/../etc/passwd"), null, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("path traversal");
    }

    @Test
    void exportToExcel_WithRecordLimit_ShouldThrowOversizeException() {
        List<Tender> largeList = java.util.stream.IntStream.range(0, 20001)
                .mapToObj(i -> Tender.builder()
                        .id((long) i)
                        .title("标讯 " + i)
                        .source("测试来源")
                        .budget(new BigDecimal("1000000.00"))
                        .status(Tender.Status.PENDING_ASSIGNMENT)
                        .build())
                .toList();
        when(tenderRepository.findAll(any(org.springframework.data.domain.Pageable.class)))
                .thenAnswer(invocation -> {
                    org.springframework.data.domain.Pageable pageable = invocation.getArgument(0);
                    int start = (int) pageable.getOffset();
                    int end = Math.min(start + pageable.getPageSize(), largeList.size());
                    return new PageImpl<>(largeList.subList(start, end), pageable, largeList.size());
                });
        Path outputPath = Path.of("/tmp/test_large_tenders.xlsx");

        assertThatThrownBy(() -> excelExportService.exportToExcel("tenders", outputPath, null, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("数据量");
    }

    @Test
    void exportToExcel_ShouldLogAuditEntry() throws Exception {
        Page<Tender> page = new PageImpl<>(List.of(testTender), PageRequest.of(0, 1000), 1);
        when(tenderRepository.findAll(any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(page)
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(1, 1000), 1));
        Path outputPath = Path.of("/tmp/test_audit_tenders.xlsx");

        excelExportService.exportToExcel("tenders", outputPath, null, 1L);

        verify(auditLogService).log(any(AuditLogService.AuditLogEntry.class));
        deleteIfExists(outputPath);
    }

    @Test
    void exportToExcel_LegacyMethod_ShouldStillWork() throws Exception {
        Page<Tender> page = new PageImpl<>(List.of(testTender), PageRequest.of(0, 1000), 1);
        when(tenderRepository.findAll(any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(page)
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(1, 1000), 1));
        Path outputPath = Path.of("/tmp/test_legacy_tenders.xlsx");

        long fileSize = excelExportService.exportToExcel("tenders", outputPath, null);

        assertThat(fileSize).isGreaterThan(0);
        deleteIfExists(outputPath);
    }
}
