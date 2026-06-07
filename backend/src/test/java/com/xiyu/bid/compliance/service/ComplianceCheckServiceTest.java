package com.xiyu.bid.compliance.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.compliance.dto.ComplianceCheckResultDTO;
import com.xiyu.bid.compliance.dto.ComplianceIssue;
import com.xiyu.bid.compliance.dto.RiskAssessmentDTO;
import com.xiyu.bid.compliance.entity.ComplianceCheckResult;
import com.xiyu.bid.compliance.entity.ComplianceRule;
import com.xiyu.bid.compliance.repository.ComplianceCheckResultRepository;
import com.xiyu.bid.compliance.repository.ComplianceRuleRepository;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.service.ProjectAccessScopeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ComplianceCheckServiceTest {

    private ComplianceRuleRepository complianceRuleRepository;
    private ComplianceCheckResultRepository complianceCheckResultRepository;
    private ProjectRepository projectRepository;
    private TenderRepository tenderRepository;
    private ExperienceComplianceEvaluator experienceComplianceEvaluator;
    private ProjectAccessScopeService projectAccessScopeService;
    private ComplianceProjectAccessGuard accessGuard;
    private ComplianceTargetLoader targetLoader;
    private ComplianceCheckService service;

    @BeforeEach
    void setUp() {
        complianceRuleRepository = mock(ComplianceRuleRepository.class);
        complianceCheckResultRepository = mock(ComplianceCheckResultRepository.class);
        projectRepository = mock(ProjectRepository.class);
        tenderRepository = mock(TenderRepository.class);
        experienceComplianceEvaluator = mock(ExperienceComplianceEvaluator.class);
        projectAccessScopeService = mock(ProjectAccessScopeService.class);
        accessGuard = new ComplianceProjectAccessGuard(projectRepository, projectAccessScopeService);
        targetLoader = new ComplianceTargetLoader(projectRepository, tenderRepository);
        service = new ComplianceCheckService(
                complianceRuleRepository,
                complianceCheckResultRepository,
                experienceComplianceEvaluator,
                accessGuard,
                targetLoader
        );
    }

    @Test
    void checkProjectCompliance_ShouldPersistSummarizedResult() {
        Project project = Project.builder()
                .id(10L)
                .status(Project.Status.BIDDING)
                .build();
        ComplianceRule failingRule = ComplianceRule.builder()
                .id(1L)
                .name("qualification")
                .ruleType(ComplianceRule.RuleType.QUALIFICATION)
                .ruleDefinition("{\"minLevel\":\"A\",\"required\":true}")
                .build();

        when(projectRepository.findById(10L)).thenReturn(Optional.of(project));
        when(complianceRuleRepository.findByEnabledTrue()).thenReturn(List.of(failingRule));
        when(complianceCheckResultRepository.save(any(ComplianceCheckResult.class)))
                .thenAnswer(invocation -> {
                    ComplianceCheckResult saved = invocation.getArgument(0);
                    saved.setId(100L);
                    return saved;
                });

        ComplianceCheckResultDTO result = service.checkProjectCompliance(10L);

        assertThat(result.getId()).isEqualTo(100L);
        assertThat(result.getOverallStatus()).isEqualTo(ComplianceCheckResult.Status.NON_COMPLIANT);
        assertThat(result.getRiskScore()).isEqualTo(75);
        assertThat(result.getIssues()).hasSize(1);
        verify(complianceCheckResultRepository).save(any(ComplianceCheckResult.class));
    }

    @Test
    void checkProjectCompliance_ShouldDelegateExperienceRulesToCaseLibraryEvaluator() {
        Project project = Project.builder()
                .id(11L)
                .status(Project.Status.BIDDING)
                .sourceModule("智慧园区")
                .build();
        ComplianceRule experienceRule = ComplianceRule.builder()
                .id(3L)
                .name("experience")
                .ruleType(ComplianceRule.RuleType.EXPERIENCE)
                .ruleDefinition("{\"minYears\":1,\"minProjects\":2}")
                .build();

        when(projectRepository.findById(11L)).thenReturn(Optional.of(project));
        when(complianceRuleRepository.findByEnabledTrue()).thenReturn(List.of(experienceRule));
        when(experienceComplianceEvaluator.evaluate(eq(experienceRule), eq(project), any(ObjectMapper.class)))
                .thenReturn(ComplianceIssueFactory.build(
                        experienceRule,
                        ComplianceIssue.Severity.MEDIUM,
                        "historical case library does not contain enough recent winning projects within 1 year(s): found 1, required 2",
                        "Add more matching winning cases or relax the experience threshold",
                        false
                ));
        when(complianceCheckResultRepository.save(any(ComplianceCheckResult.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ComplianceCheckResultDTO result = service.checkProjectCompliance(11L);

        assertThat(result.getOverallStatus()).isEqualTo(ComplianceCheckResult.Status.NON_COMPLIANT);
        assertThat(result.getRiskScore()).isEqualTo(50);
        assertThat(result.getIssues()).singleElement().satisfies(issue -> {
            assertThat(issue.getPassed()).isFalse();
            assertThat(issue.getSeverity()).isEqualTo(ComplianceIssue.Severity.MEDIUM);
            assertThat(issue.getDescription()).contains("historical case library does not contain enough recent winning projects");
        });
    }

    @Test
    void checkProjectCompliance_ShouldRejectNullId() {
        assertThatThrownBy(() -> service.checkProjectCompliance(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Project ID cannot be null");
    }

    @Test
    void assessRisk_ShouldFallbackToPolicyWhenNoResultExists() {
        Project project = Project.builder()
                .id(8L)
                .status(Project.Status.BIDDING)
                .build();
        when(projectRepository.findById(8L)).thenReturn(Optional.of(project));
        when(complianceCheckResultRepository.findTopByProjectIdOrderByCheckedAtDesc(8L))
                .thenReturn(Optional.empty());

        RiskAssessmentDTO result = service.assessRisk(8L);

        assertThat(result.getRiskScore()).isEqualTo(50);
        assertThat(result.getRiskLevel()).isEqualTo(RiskAssessmentDTO.RiskLevel.MEDIUM);
    }

    @Test
    void checkTenderCompliance_ShouldPersistTenderResult() {
        Tender tender = Tender.builder()
                .id(12L)
                .deadline(LocalDateTime.now().plusDays(1))
                .build();
        ComplianceRule deadlineRule = ComplianceRule.builder()
                .id(2L)
                .name("deadline")
                .ruleType(ComplianceRule.RuleType.DEADLINE)
                .ruleDefinition("{}")
                .build();

        when(tenderRepository.findById(12L)).thenReturn(Optional.of(tender));
        when(projectAccessScopeService.currentUserHasAdminAccess()).thenReturn(true);
        when(complianceRuleRepository.findByEnabledTrue()).thenReturn(List.of(deadlineRule));
        when(complianceCheckResultRepository.save(any(ComplianceCheckResult.class)))
                .thenAnswer(invocation -> {
                    ComplianceCheckResult saved = invocation.getArgument(0);
                    saved.setId(101L);
                    return saved;
                });

        ComplianceCheckResultDTO result = service.checkTenderCompliance(12L);

        assertThat(result.getId()).isEqualTo(101L);
        assertThat(result.getTenderId()).isEqualTo(12L);
        assertThat(result.getOverallStatus()).isEqualTo(ComplianceCheckResult.Status.COMPLIANT);
        assertThat(result.getRiskScore()).isZero();
    }

    @Test
    void checkProjectCompliance_ShouldThrowWhenProjectMissing() {
        when(projectRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.checkProjectCompliance(99L))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Project not found with id: 99");
    }
}
