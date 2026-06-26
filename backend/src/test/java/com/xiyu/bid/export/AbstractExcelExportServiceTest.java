package com.xiyu.bid.export;

import com.xiyu.bid.audit.service.IAuditLogService;
import com.xiyu.bid.config.ExportConfig;
import com.xiyu.bid.entity.Case;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.Qualification;
import com.xiyu.bid.entity.Template;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.export.service.ExcelExportService;
import com.xiyu.bid.repository.CaseRepository;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.repository.QualificationRepository;
import com.xiyu.bid.repository.TemplateRepository;
import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.service.ProjectAccessScopeService;
import com.xiyu.bid.tender.service.TenderProjectAccessGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
abstract class AbstractExcelExportServiceTest {

    @Mock
    protected TenderRepository tenderRepository;

    @Mock
    protected ProjectRepository projectRepository;

    @Mock
    protected QualificationRepository qualificationRepository;

    @Mock
    protected CaseRepository caseRepository;

    @Mock
    protected TemplateRepository templateRepository;

    @Mock
    protected IAuditLogService auditLogService;

    @Mock
    protected ProjectAccessScopeService projectAccessScopeService;

    @Mock
    protected TenderProjectAccessGuard tenderProjectAccessGuard;

    protected ExcelExportService excelExportService;
    protected Tender testTender;
    protected Project testProject;
    protected Qualification testQualification;
    protected Case testCase;
    protected Template testTemplate;

    @BeforeEach
    void setUpExcelExportFixture() {
        ExportConfig exportConfig = new ExportConfig();
        exportConfig.setMaxRecords(10000);
        exportConfig.setMaxFileSizeBytes(52_428_800L);
        exportConfig.setAuditEnabled(true);
        exportConfig.setMaxExportsPerHour(10);
        exportConfig.setQueryTimeoutSeconds(120);

        excelExportService = new ExcelExportService(
                tenderRepository,
                projectRepository,
                qualificationRepository,
                caseRepository,
                templateRepository,
                exportConfig,
                auditLogService,
                projectAccessScopeService,
                tenderProjectAccessGuard
        );
        lenient().when(projectAccessScopeService.currentUserHasAdminAccess()).thenReturn(true);
        // admin 用户可见所有 tender（filterVisibleTenders 返回传入列表）
        lenient().when(tenderProjectAccessGuard.filterVisibleTenders(any())).thenAnswer(inv -> inv.getArgument(0));

        testTender = Tender.builder()
                .id(1L)
                .title("测试标讯项目")
                .source("政府采购网")
                .budget(new BigDecimal("1000000.00"))
                .deadline(LocalDateTime.of(2024, 6, 30, 17, 0))
                .status(Tender.Status.PENDING_ASSIGNMENT)
                .aiScore(85)
                .riskLevel(Tender.RiskLevel.LOW)
                .createdAt(LocalDateTime.of(2024, 3, 1, 10, 0))
                .updatedAt(LocalDateTime.of(2024, 3, 1, 10, 0))
                .build();

        testProject = Project.builder()
                .id(1L)
                .name("测试投标项目")
                .tenderId(1L)
                .status(Project.Status.BIDDING)
                .managerId(1L)
                .startDate(LocalDateTime.of(2024, 3, 1, 10, 0))
                .endDate(LocalDateTime.of(2024, 6, 30, 17, 0))
                .createdAt(LocalDateTime.of(2024, 3, 1, 10, 0))
                .updatedAt(LocalDateTime.of(2024, 3, 1, 10, 0))
                .build();

        testQualification = Qualification.builder()
                .id(1L)
                .name("建筑工程施工总承包资质")
                .type(Qualification.Type.CONSTRUCTION)
                .level("FIRST")
                .issueDate(LocalDate.of(2020, 1, 1))
                .expiryDate(LocalDate.of(2025, 12, 31))
                .fileUrl("/files/qualification.pdf")
                .createdAt(LocalDateTime.of(2024, 3, 1, 10, 0))
                .updatedAt(LocalDateTime.of(2024, 3, 1, 10, 0))
                .build();

        testCase = Case.builder()
                .id(1L)
                .title("智慧城市建设项目")
                .industry(Case.Industry.INFRASTRUCTURE)
                .outcome(Case.Outcome.WON)
                .amount(new BigDecimal("5000000.00"))
                .projectDate(LocalDate.of(2023, 6, 15))
                .description("智慧城市综合管理平台建设")
                .customerName("某市人民政府")
                .locationName("北京市")
                .projectPeriod("12个月")
                .viewCount(100L)
                .useCount(5L)
                .createdAt(LocalDateTime.of(2024, 3, 1, 10, 0))
                .updatedAt(LocalDateTime.of(2024, 3, 1, 10, 0))
                .build();

        testTemplate = Template.builder()
                .id(1L)
                .name("技术标模板")
                .category(Template.Category.TECHNICAL)
                .fileUrl("/files/template.docx")
                .description("技术标书标准模板")
                .currentVersion("V1.0")
                .fileSize("2.5MB")
                .createdBy(1L)
                .createdAt(LocalDateTime.of(2024, 3, 1, 10, 0))
                .updatedAt(LocalDateTime.of(2024, 3, 1, 10, 0))
                .build();
    }

    protected void deleteIfExists(Path outputPath) throws Exception {
        Files.deleteIfExists(outputPath);
    }
}
