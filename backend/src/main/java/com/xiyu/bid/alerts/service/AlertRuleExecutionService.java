// Input: alert rule repositories and alert history service
// Output: Rule-specific alert execution orchestration for alerts-owned rules
// Pos: Service/业务层
package com.xiyu.bid.alerts.service;

import com.xiyu.bid.alerts.dto.AlertHistoryCreateRequest;
import com.xiyu.bid.alerts.entity.AlertHistory;
import com.xiyu.bid.alerts.entity.AlertRule;
import com.xiyu.bid.compliance.dto.RiskAssessmentDTO;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.projectworkflow.entity.ProjectDocument;
import com.xiyu.bid.projectworkflow.repository.ProjectDocumentRepository;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.repository.TenderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class AlertRuleExecutionService {

    private final AlertHistoryService alertHistoryService;
    private final ProjectRepository projectRepository;
    private final TenderRepository tenderRepository;
    private final ProjectDocumentRepository projectDocumentRepository;

    /** DEADLINE 规则关注的状态集合：待分配 + 跟踪中 */
    private static final Set<Tender.Status> DEADLINE_RELEVANT_STATUSES = Set.of(
            Tender.Status.PENDING_ASSIGNMENT, Tender.Status.TRACKING);

    /**
     * 风险等级 → 代表性分数，复用 RiskAssessmentDTO.RiskLevel 的 minScore + 15（与历史值 15/45/75 等价）。
     * <p>Tender.RiskLevel 自身不含分数区间，统一通过 RiskAssessmentDTO.RiskLevel 取值避免硬编码漂移。</p>
     */
    private static int riskLevelToScore(Tender.RiskLevel level) {
        return RiskAssessmentDTO.RiskLevel.valueOf(level.name()).getMinScore() + 15;
    }

    public void execute(AlertRule rule) {
        log.debug("Checking alert rule: {} (Type: {}, Condition: {}, Threshold: {})",
                rule.getName(), rule.getType(), rule.getCondition(), rule.getThreshold());

        switch (rule.getType()) {
            case DEADLINE -> checkDeadlineAlert(rule);
            case RISK -> checkRiskAlert(rule);
            case DOCUMENT -> checkDocumentAlert(rule);
            case BUDGET, QUALIFICATION_EXPIRY, DEPOSIT_RETURN, PERFORMANCE_EXPIRY, CA_EXPIRY, CA_BORROW_OVERDUE ->
                    throw new IllegalArgumentException("Cross-module alert rule must be orchestrated outside alerts core: " + rule.getType());
        }
    }

    private void checkDeadlineAlert(AlertRule rule) {
        LocalDateTime now = LocalDateTime.now();
        int thresholdDays = rule.getThreshold().intValue();

        for (Tender tender : tenderRepository.findByStatusIn(DEADLINE_RELEVANT_STATUSES)) {
            if (tender.getDeadline() == null) {
                continue;
            }

            long daysUntilDeadline = ChronoUnit.DAYS.between(now, tender.getDeadline());
            boolean shouldAlert = switch (rule.getCondition()) {
                case LESS_THAN -> daysUntilDeadline <= thresholdDays && daysUntilDeadline >= 0;
                case GREATER_THAN -> daysUntilDeadline > thresholdDays;
                case EQUALS -> daysUntilDeadline == thresholdDays;
                // 数字场景不适用 CONTAINS：天数无法做包含关系判断
                case CONTAINS -> false;
            };

            if (daysUntilDeadline < 0) {
                shouldAlert = true;
            }

            if (shouldAlert) {
                String deadlineStatus = daysUntilDeadline < 0
                        ? "已过期 " + Math.abs(daysUntilDeadline) + " 天"
                        : "还剩 " + daysUntilDeadline + " 天";
                createAlert(rule, tender.getId(), "Tender",
                        String.format("标讯 %s 截止日期 %s (截止日期: %s)",
                                tender.getTitle(), deadlineStatus, tender.getDeadline()));
            }
        }
    }

    private void checkRiskAlert(AlertRule rule) {
        int thresholdScore = rule.getThreshold().intValue();
        RiskAssessmentDTO.RiskLevel thresholdLevel = RiskAssessmentDTO.RiskLevel.fromScore(thresholdScore);

        for (Tender tender : tenderRepository.findAll()) {
            if (tender.getRiskLevel() == null) {
                continue;
            }

            int tenderRiskScore = riskLevelToScore(tender.getRiskLevel());

            boolean shouldAlert = switch (rule.getCondition()) {
                case GREATER_THAN -> tenderRiskScore > thresholdScore;
                case LESS_THAN -> tenderRiskScore < thresholdScore;
                case EQUALS -> tenderRiskScore == thresholdScore;
                // 数字场景不适用 CONTAINS：风险分数无法做包含关系判断
                case CONTAINS -> false;
            };

            if (!shouldAlert && rule.getCondition() == AlertRule.ConditionType.GREATER_THAN) {
                shouldAlert = tender.getRiskLevel() == Tender.RiskLevel.HIGH
                        && thresholdLevel != RiskAssessmentDTO.RiskLevel.HIGH;
            }

            if (shouldAlert) {
                createAlert(rule, tender.getId(), "Tender",
                        String.format("标讯 %s 风险等级为 %s，需要注意 (风险分数: %d)",
                                tender.getTitle(), tender.getRiskLevel().name(), tenderRiskScore));
            }
        }
    }

    private void checkDocumentAlert(AlertRule rule) {
        int maxMissingDocs = rule.getThreshold().intValue();

        for (Project project : projectRepository.findActiveProjects()) {
            // 每项目只查一次文档列表，避免 N+1 查询
            List<ProjectDocument> docs = projectDocumentRepository
                    .findByProjectIdOrderByCreatedAtDesc(project.getId());
            int missingDocCount = 0;
            String missingDocs;

            switch (project.getStatus()) {
                case BIDDING -> {
                    missingDocCount += hasDocument(docs, "资质文件") ? 0 : 1;
                    missingDocCount += hasDocument(docs, "技术方案") ? 0 : 1;
                    missingDocCount += hasDocument(docs, "商务方案") ? 0 : 1;
                    missingDocs = "资质文件、技术方案、商务方案";
                }
                case EVALUATING -> {
                    missingDocCount += hasDocument(docs, "标书完整版") ? 0 : 1;
                    missingDocCount += hasDocument(docs, "审核记录") ? 0 : 1;
                    missingDocCount += hasDocument(docs, "最终标书") ? 0 : 1;
                    missingDocs = "标书完整版、审核记录、最终标书";
                }
                default -> {
                    continue;
                }
            }

            boolean shouldAlert = switch (rule.getCondition()) {
                case GREATER_THAN -> missingDocCount > maxMissingDocs;
                case LESS_THAN -> missingDocCount < maxMissingDocs;
                case EQUALS -> missingDocCount == maxMissingDocs;
                // 数字场景不适用 CONTAINS：缺失文档数量无法做包含关系判断
                case CONTAINS -> false;
            };

            if (shouldAlert || missingDocCount > 0) {
                createAlert(rule, project.getId(), "Project",
                        String.format("项目 %s (状态: %s) 缺少 %d 个必需文档: %s",
                                project.getName(), project.getStatus(), missingDocCount, missingDocs));
            }
        }
    }

    /**
     * 检查已查询的文档列表中是否包含指定类型。
     * <p>基于真实业务数据：检查文档名是否包含指定类型关键字。
     * 例如 docType="资质文件" 时，会匹配 name 含"资质文件"的文档。</p>
     * <p>注：alert 业务传入的 docType 是中文文档类型名（"资质文件"、"技术方案"等），
     * 而 ProjectDocument.name 是实际文件名，因此使用包含匹配而非精确相等。</p>
     *
     * @param docs   已查询的项目文档列表（避免 N+1 查询，由调用方一次性加载）
     * @param docType 文档类型关键字（中文名称）
     * @return true 表示项目已上传该类型文档；false 表示缺失
     */
    private boolean hasDocument(List<ProjectDocument> docs, String docType) {
        return docs.stream().anyMatch(d -> d.getName() != null && d.getName().contains(docType));
    }

    private void createAlert(AlertRule rule, Long entityId, String entityType, String message) {
        AlertHistoryCreateRequest request = new AlertHistoryCreateRequest();
        request.setRuleId(rule.getId());
        request.setLevel(calculateSeverity(rule));
        request.setMessage(message);
        request.setRelatedId(String.format("%s:%s", entityType, entityId));
        alertHistoryService.createAlertHistory(request);
        log.info("Alert created: Rule={}, Entity={}, Message={}", rule.getName(), entityType, message);
    }

    private AlertHistory.AlertLevel calculateSeverity(AlertRule rule) {
        return switch (rule.getType()) {
            case BUDGET -> AlertHistory.AlertLevel.HIGH;
            case DEADLINE -> {
                int days = rule.getThreshold().intValue();
                yield days <= 1 ? AlertHistory.AlertLevel.CRITICAL
                        : days <= 3 ? AlertHistory.AlertLevel.HIGH
                        : AlertHistory.AlertLevel.MEDIUM;
            }
            case RISK, DEPOSIT_RETURN -> AlertHistory.AlertLevel.MEDIUM;
            case DOCUMENT -> AlertHistory.AlertLevel.LOW;
            case QUALIFICATION_EXPIRY, PERFORMANCE_EXPIRY, CA_EXPIRY, CA_BORROW_OVERDUE -> AlertHistory.AlertLevel.HIGH;
        };
    }
}
