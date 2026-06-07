package com.xiyu.bid.compliance.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.compliance.domain.BidDocumentQualityCheckPolicy;
import com.xiyu.bid.compliance.domain.BidDocumentQualityCheckPolicy.QualityCheckResult;
import com.xiyu.bid.compliance.domain.BidDocumentQualityCheckPolicy.QualityIssue;
import com.xiyu.bid.compliance.dto.ComplianceCheckResultDTO;
import com.xiyu.bid.compliance.dto.ComplianceIssue;
import com.xiyu.bid.compliance.entity.ComplianceCheckResult;
import com.xiyu.bid.compliance.repository.ComplianceCheckResultRepository;
import com.xiyu.bid.compliance.service.ComplianceTargetLoader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 标书文档质量核查应用服务.
 * 负责编排文档加载、调用纯核心策略、持久化结果.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BidDocumentQualityCheckAppService {

    /** ObjectMapper实例. */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    /** 风险分数临界值. */
    private static final int RISK_SCORE_MAX = 100;
    /** 严重问题权重. */
    private static final int CRITICAL_WEIGHT = 25;
    /** 警告问题权重. */
    private static final int WARNING_WEIGHT = 10;

    /** 结果仓库. */
    private final ComplianceCheckResultRepository resultRepository;
    /** 目标加载器. */
    private final ComplianceTargetLoader targetLoader;

    /**
     * 对指定项目执行标书文档质量核查.
     *
     * @param projectId 项目ID
     * @return 核查结果DTO
     */
    @Transactional
    public ComplianceCheckResultDTO checkBidDocumentQuality(
            final Long projectId) {
        requireId(projectId, "Project ID");

        log.info("Starting bid document quality check for project: {}",
                projectId);

        // 加载项目关联的标书文档内容和招标文件内容
        String documentContent = targetLoader.loadProjectDocumentContent(
                projectId);
        String tenderText = targetLoader.loadProjectTenderText(projectId);

        // 调用纯核心策略
        QualityCheckResult qualityResult = BidDocumentQualityCheckPolicy.check(
                documentContent, tenderText);

        // 转换为合规Issue列表
        List<ComplianceIssue> issues = mapToComplianceIssues(
                qualityResult.issues());

        // 计算风险分数
        int riskScore = calculateRiskScore(qualityResult);

        // 持久化结果
        ComplianceCheckResult result = persistResult(projectId,
                qualityResult, issues, riskScore);

        log.info("Bid document quality check completed for project {}: "
                        + "status={}, issues={}",
                projectId, qualityResult.overallStatus(),
                qualityResult.totalIssues());

        return toDTO(result, issues);
    }

    /**
     * 获取项目最新的标书文档质量核查结果.
     *
     * @param projectId 项目ID
     * @return 核查结果DTO，若无则返回null
     */
    public ComplianceCheckResultDTO getLatestQualityCheckResult(
            final Long projectId) {
        requireId(projectId, "Project ID");

        return resultRepository
                .findTopByProjectIdAndCheckTypeOrderByCheckedAtDesc(
                        projectId,
                        ComplianceCheckResult.CheckType.BID_DOCUMENT_QUALITY
                )
                .map(this::toDTOWithParsedIssues)
                .orElse(null);
    }

    // ── 内部方法 ─────────────────────────────────────────────────────────────

    private ComplianceCheckResult persistResult(
            final Long projectId,
            final QualityCheckResult qualityResult,
            final List<ComplianceIssue> issues,
            final int riskScore) {
        ComplianceCheckResult result = ComplianceCheckResult.builder()
                .projectId(projectId)
                .overallStatus(mapOverallStatus(
                        qualityResult.overallStatus()))
                .riskScore(riskScore)
                .checkType(ComplianceCheckResult.CheckType.BID_DOCUMENT_QUALITY)
                .checkedAt(LocalDateTime.now())
                .checkedBy("system")
                .build();

        try {
            result.setCheckDetails(OBJECT_MAPPER.writeValueAsString(issues));
        } catch (JsonProcessingException exception) {
            log.error("Error serializing quality check details", exception);
        }

        return resultRepository.save(result);
    }

    private List<ComplianceIssue> mapToComplianceIssues(
            final List<QualityIssue> qualityIssues) {
        return qualityIssues.stream()
                .map(this::mapToComplianceIssue)
                .toList();
    }

    private ComplianceIssue mapToComplianceIssue(final QualityIssue issue) {
        return ComplianceIssue.builder()
                .ruleId(null)
                .ruleName(issue.checkName())
                .ruleType(null)
                .severity(mapSeverity(issue.severity()))
                .description(issue.description())
                .recommendation(issue.suggestion())
                .passed(issue.passed())
                .build();
    }

    private ComplianceIssue.Severity mapSeverity(
            final BidDocumentQualityCheckPolicy.IssueSeverity severity) {
        return switch (severity) {
            case PASS -> ComplianceIssue.Severity.LOW;
            case WARNING -> ComplianceIssue.Severity.MEDIUM;
            case FAIL -> ComplianceIssue.Severity.CRITICAL;
            case MANUAL -> ComplianceIssue.Severity.HIGH;
        };
    }

    private ComplianceCheckResult.Status mapOverallStatus(
            final BidDocumentQualityCheckPolicy.OverallStatus status) {
        return switch (status) {
            case PASS -> ComplianceCheckResult.Status.COMPLIANT;
            case WARNING -> ComplianceCheckResult.Status.WARNING;
            case FAIL -> ComplianceCheckResult.Status.NON_COMPLIANT;
            case MANUAL -> ComplianceCheckResult.Status.PARTIAL_COMPLIANT;
        };
    }

    private int calculateRiskScore(final QualityCheckResult result) {
        int baseScore = result.criticalCount() * CRITICAL_WEIGHT
                + result.warningCount() * WARNING_WEIGHT;
        return Math.min(RISK_SCORE_MAX, baseScore);
    }

    private ComplianceCheckResultDTO toDTO(
            final ComplianceCheckResult result,
            final List<ComplianceIssue> issues) {
        return ComplianceCheckResultDTO.builder()
                .id(result.getId())
                .projectId(result.getProjectId())
                .tenderId(result.getTenderId())
                .overallStatus(result.getOverallStatus())
                .issues(issues)
                .riskScore(result.getRiskScore())
                .checkedAt(result.getCheckedAt())
                .checkedBy(result.getCheckedBy())
                .build();
    }

    private ComplianceCheckResultDTO toDTOWithParsedIssues(
            final ComplianceCheckResult result) {
        List<ComplianceIssue> issues = parseIssues(result.getCheckDetails());
        return toDTO(result, issues);
    }

    @SuppressWarnings("unchecked")
    private List<ComplianceIssue> parseIssues(
            final String checkDetails) {
        if (checkDetails == null || checkDetails.isBlank()) {
            return List.of();
        }
        try {
            return OBJECT_MAPPER.readValue(checkDetails,
                    OBJECT_MAPPER.getTypeFactory()
                            .constructCollectionType(
                                    List.class, ComplianceIssue.class));
        } catch (JsonProcessingException exception) {
            log.error("Error parsing check details", exception);
            return List.of();
        }
    }

    private void requireId(final Long id, final String label) {
        if (id == null) {
            throw new IllegalArgumentException(
                    label + " cannot be null");
        }
    }
}
