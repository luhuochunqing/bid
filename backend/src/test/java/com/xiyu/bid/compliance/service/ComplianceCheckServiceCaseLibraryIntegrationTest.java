// Input: compliance service, repositories, and case library fixtures
// Output: integration coverage for case-library-backed compliance checks
// Pos: Test/集成测试
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.compliance.service;

import com.xiyu.bid.compliance.dto.ComplianceIssue;
import com.xiyu.bid.compliance.entity.ComplianceCheckResult;
import com.xiyu.bid.compliance.entity.ComplianceRule;
import com.xiyu.bid.compliance.repository.ComplianceCheckResultRepository;
import com.xiyu.bid.compliance.repository.ComplianceRuleRepository;
import com.xiyu.bid.entity.Case;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.platform.util.PasswordEncryptionUtil;
import com.xiyu.bid.repository.CaseRepository;
import com.xiyu.bid.repository.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.main.allow-bean-definition-overriding=true")
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@Transactional
@WithMockUser(roles = {"ADMIN"})
class ComplianceCheckServiceCaseLibraryIntegrationTest {

    @Autowired
    private ComplianceCheckService complianceCheckService;

    @Autowired
    private ComplianceRuleRepository complianceRuleRepository;

    @Autowired
    private ComplianceCheckResultRepository complianceCheckResultRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private CaseRepository caseRepository;

    private Project project;
    private Case recentWinningCase;
    private Case olderWinningCase;

    @TestConfiguration
    static class TestBeans {
        @Bean(name = "passwordEncryptionUtil")
        @Primary
        PasswordEncryptionUtil passwordEncryptionUtil() {
            return new PasswordEncryptionUtil() {
                @Override
                public void initialize() {
                }

                @Override
                public String encrypt(String plainPassword) {
                    return plainPassword;
                }

                @Override
                public String decrypt(String encryptedPassword) {
                    return encryptedPassword;
                }

                @Override
                public boolean isKeyValid() {
                    return true;
                }
            };
        }
    }

    @BeforeEach
    void setUp() {
        complianceCheckResultRepository.deleteAll();
        complianceRuleRepository.deleteAll();
        caseRepository.deleteAll();
        projectRepository.deleteAll();

        project = projectRepository.save(Project.builder()
                .name("合规案例库消费验证项目")
                .tenderId(88001L)
                .status(Project.Status.BIDDING)
                .managerId(9901L)
                .teamMembers(List.of(9901L))
                .sourceModule("智慧园区")
                .sourceCustomer("测试客户")
                .sourceReasoningSummary("园区项目经验沉淀")
                .build());

        recentWinningCase = caseRepository.save(Case.builder()
                .title("智慧园区近年中标案例")
                .industry(Case.Industry.INFRASTRUCTURE)
                .outcome(Case.Outcome.WON)
                .amount(new BigDecimal("120.00"))
                .projectDate(LocalDate.now().minusMonths(6))
                .description("最近一年的归档摘要")
                .customerName("测试客户")
                .locationName("杭州")
                .projectPeriod("2025-01-01 - 2025-12-31")
                .productLine("智慧园区")
                .archiveSummary("最近一年的归档摘要")
                .documentSnapshotText("最近一年的正文摘录")
                .status("PUBLISHED")
                .visibility("INTERNAL")
                .build());

        olderWinningCase = caseRepository.save(Case.builder()
                .title("智慧园区历史中标案例")
                .industry(Case.Industry.INFRASTRUCTURE)
                .outcome(Case.Outcome.WON)
                .amount(new BigDecimal("150.00"))
                .projectDate(LocalDate.now().minusYears(2))
                .description("两年前的归档摘要")
                .customerName("测试客户")
                .locationName("杭州")
                .projectPeriod("2023-01-01 - 2023-12-31")
                .productLine("智慧园区")
                .archiveSummary("两年前的归档摘要")
                .documentSnapshotText("两年前的正文摘录")
                .status("PUBLISHED")
                .visibility("INTERNAL")
                .build());

        complianceRuleRepository.save(ComplianceRule.builder()
                .name("三年内至少两项中标案例")
                .ruleType(ComplianceRule.RuleType.EXPERIENCE)
                .ruleDefinition("""
                        {"minYears": 3, "minProjects": 2}
                        """)
                .description("命中历史案例库的真实消费路径")
                .enabled(true)
                .build());

        complianceRuleRepository.save(ComplianceRule.builder()
                .name("一年内至少两项中标案例")
                .ruleType(ComplianceRule.RuleType.EXPERIENCE)
                .ruleDefinition("""
                        {"minYears": 1, "minProjects": 2}
                        """)
                .description("窄窗口误命中场景")
                .enabled(true)
                .build());
    }

    @Test
    void checkProjectCompliance_ShouldConsumeCaseLibraryAndRecordHitAndMiss() {
        var result = complianceCheckService.checkProjectCompliance(project.getId());

        assertThat(result.getProjectId()).isEqualTo(project.getId());
        assertThat(result.getOverallStatus()).isEqualTo(ComplianceCheckResult.Status.NON_COMPLIANT);
        assertThat(result.getRiskScore()).isEqualTo(25);
        assertThat(result.getIssues()).hasSize(2);

        Map<String, ComplianceIssue> issuesByName = result.getIssues().stream()
                .collect(java.util.stream.Collectors.toMap(ComplianceIssue::getRuleName, issue -> issue));

        assertThat(issuesByName).containsKeys("三年内至少两项中标案例", "一年内至少两项中标案例");
        assertThat(issuesByName.get("三年内至少两项中标案例").getPassed()).isTrue();
        assertThat(issuesByName.get("三年内至少两项中标案例").getDescription())
                .contains("historical case library");
        assertThat(issuesByName.get("一年内至少两项中标案例").getPassed()).isFalse();
        assertThat(issuesByName.get("一年内至少两项中标案例").getDescription())
                .contains("does not contain enough recent winning projects");

        assertThat(complianceCheckResultRepository.findByProjectId(project.getId())).hasSize(1);
        ComplianceCheckResult savedResult = complianceCheckResultRepository
                .findTopByProjectIdOrderByCheckedAtDesc(project.getId())
                .orElseThrow();
        assertThat(savedResult.getOverallStatus()).isEqualTo(ComplianceCheckResult.Status.NON_COMPLIANT);
        assertThat(savedResult.getRiskScore()).isEqualTo(25);
        assertThat(savedResult.getCheckDetails()).contains("三年内至少两项中标案例");
        assertThat(savedResult.getCheckDetails()).contains("一年内至少两项中标案例");

        assertThat(caseRepository.countWonCasesByFilters(
                Case.Industry.INFRASTRUCTURE,
                "智慧园区",
                LocalDate.now().minusYears(3).plusDays(1),
                null)).isEqualTo(2L);
        assertThat(caseRepository.countWonCasesByFilters(
                Case.Industry.INFRASTRUCTURE,
                "智慧园区",
                LocalDate.now().minusYears(1).plusDays(1),
                null)).isEqualTo(1L);
        assertThat(recentWinningCase.getId()).isNotNull();
        assertThat(olderWinningCase.getId()).isNotNull();
    }
}
