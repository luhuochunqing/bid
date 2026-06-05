package com.xiyu.bid.export;

import com.xiyu.bid.config.ExportConfig;
import com.xiyu.bid.export.service.ExcelExportService;
import com.xiyu.bid.repository.CaseRepository;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.repository.QualificationRepository;
import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.repository.TemplateRepository;
import com.xiyu.bid.audit.service.IAuditLogService;
import com.xiyu.bid.service.ProjectAccessScopeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;

/**
 * Security tests for ExcelExportService path validation.
 * Tests to verify directory traversal attacks are prevented.
 */
@ExtendWith(MockitoExtension.class)
class ExcelExportServiceSecurityTest {

    @Mock
    private TenderRepository tenderRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private QualificationRepository qualificationRepository;

    @Mock
    private CaseRepository caseRepository;

    @Mock
    private TemplateRepository templateRepository;

    @Mock
    private IAuditLogService auditLogService;

    @Mock
    private ProjectAccessScopeService projectAccessScopeService;

    private ExcelExportService excelExportService;

    private ExportConfig exportConfig;

    @BeforeEach
    void setUp() {
        exportConfig = new ExportConfig();
        exportConfig.setMaxRecords(10000);
        exportConfig.setMaxFileSizeBytes(50 * 1024 * 1024); // 50MB
        exportConfig.setMaxExportsPerHour(10);
        exportConfig.setAuditEnabled(false);

        excelExportService = new ExcelExportService(
                tenderRepository,
                projectRepository,
                qualificationRepository,
                caseRepository,
                templateRepository,
                exportConfig,
                auditLogService,
                projectAccessScopeService
        );
        lenient().when(projectAccessScopeService.currentUserHasAdminAccess()).thenReturn(true);
    }

    @Test
    void exportToExcel_WithPathTraversal_DoubleDot_ShouldThrowException() {
        // Given: Path with directory traversal attempt
        Path maliciousPath = Path.of("/tmp/../etc/passwd");

        // When & Then: Should throw exception
        assertThatThrownBy(() -> excelExportService.exportToExcel("tenders", maliciousPath, null, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("path traversal");
    }

    @Test
    void exportToExcel_WithPathTraversal_EncodedDoubleDot_ShouldThrowException() {
        // Given: Path with encoded directory traversal attempt
        Path maliciousPath = Path.of("/tmp/./../etc/passwd");

        // When & Then: Should throw exception
        assertThatThrownBy(() -> excelExportService.exportToExcel("tenders", maliciousPath, null, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("path traversal");
    }

    @Test
    void exportToExcel_WithAbsolutePathOutsideTemp_ShouldThrowException() {
        // Given: Absolute path outside temp directory
        Path maliciousPath = Path.of("/etc/passwd");

        // When & Then: Should throw exception
        assertThatThrownBy(() -> excelExportService.exportToExcel("tenders", maliciousPath, null, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("temp directory");
    }

    @Test
    void exportToExcel_WithHomeDirectoryTraversal_ShouldThrowException() {
        // Given: Path attempting to access home directory
        Path maliciousPath = Path.of("/tmp/../../home/user/.ssh/id_rsa");

        // When & Then: Should throw exception
        assertThatThrownBy(() -> excelExportService.exportToExcel("tenders", maliciousPath, null, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("path traversal");
    }

    @Test
    void exportToExcel_WithRelativePathTraversal_ShouldThrowException() {
        // Given: Relative path with directory traversal
        Path maliciousPath = Path.of("../../../etc/passwd");

        // When & Then: Should throw exception
        assertThatThrownBy(() -> excelExportService.exportToExcel("tenders", maliciousPath, null, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("path traversal");
    }

    @Test
    void exportToExcel_WithValidTempPath_ShouldNotThrowException() {
        // Given: Valid path within temp directory
        Path validPath = Path.of("/tmp/xiyu-exports/test_export.xlsx");

        // When & Then: Should not throw path validation exception
        // (It may throw other exceptions due to mocked repositories, but not path-related)
        assertThatThrownBy(() -> excelExportService.exportToExcel("tenders", validPath, null, 1L))
                .isNotInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void exportToExcel_WithSystemTempDirPath_ShouldNotThrowException() {
        // Given: Valid path within system temp directory
        String systemTempDir = System.getProperty("java.io.tmpdir");
        Path validPath = Path.of(systemTempDir, "xiyu-exports", "test_export.xlsx");

        // When & Then: Should not throw path validation exception
        assertThatThrownBy(() -> excelExportService.exportToExcel("tenders", validPath, null, 1L))
                .isNotInstanceOfAny(
                        IllegalArgumentException.class
                );
    }

    @Test
    void exportToExcel_WithNullDataType_ShouldThrowException() {
        // Given: Valid path but null data type
        Path validPath = Path.of("/tmp/test.xlsx");

        // When & Then: Should throw exception
        assertThatThrownBy(() -> excelExportService.exportToExcel(null, validPath, null, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be null");
    }

    @Test
    void exportToExcel_WithInvalidDataType_ShouldThrowException() {
        // Given: Valid path but invalid data type
        Path validPath = Path.of("/tmp/test.xlsx");

        // When & Then: Should throw exception
        assertThatThrownBy(() -> excelExportService.exportToExcel("malicious_type", validPath, null, 1L))
                .isInstanceOf(RuntimeException.class);
    }
}
