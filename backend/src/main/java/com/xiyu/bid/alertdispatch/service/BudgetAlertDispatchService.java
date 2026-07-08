package com.xiyu.bid.alertdispatch.service;

import com.xiyu.bid.alerts.dto.AlertHistoryCreateRequest;
import com.xiyu.bid.alerts.dto.AlertHistoryCreateResult;
import com.xiyu.bid.alerts.entity.AlertHistory;
import com.xiyu.bid.alerts.entity.AlertRule;
import com.xiyu.bid.alerts.service.AlertHistoryService;
import com.xiyu.bid.alerts.service.AlertNotificationOrchestrator;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.resources.repository.ExpenseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class BudgetAlertDispatchService {

    private final AlertHistoryService alertHistoryService;
    private final AlertNotificationOrchestrator alertNotificationOrchestrator;
    private final ProjectRepository projectRepository;
    private final TenderRepository tenderRepository;
    private final ExpenseRepository expenseRepository;

    public void dispatch(AlertRule rule) {
        for (Project project : projectRepository.findActiveProjects()) {
            Tender tender = tenderRepository.findById(project.getTenderId()).orElse(null);
            if (tender == null || tender.getBudget() == null || tender.getBudget().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            BigDecimal totalExpense = expenseRepository.sumAmountByProjectId(project.getId());
            BigDecimal expenseRatio = totalExpense
                    .divide(tender.getBudget(), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));

            if (shouldAlert(rule, expenseRatio)) {
                createAlert(rule, project, expenseRatio, totalExpense, tender.getBudget());
            }
        }
    }

    private boolean shouldAlert(AlertRule rule, BigDecimal expenseRatio) {
        return switch (rule.getCondition()) {
            case GREATER_THAN -> expenseRatio.compareTo(rule.getThreshold()) > 0;
            case LESS_THAN -> expenseRatio.compareTo(rule.getThreshold()) < 0;
            case EQUALS -> expenseRatio.compareTo(rule.getThreshold()) == 0;
            default -> false;
        };
    }

    private void createAlert(
            AlertRule rule,
            Project project,
            BigDecimal expenseRatio,
            BigDecimal totalExpense,
            BigDecimal budget
    ) {
        AlertHistoryCreateRequest request = new AlertHistoryCreateRequest();
        request.setRuleId(rule.getId());
        request.setLevel(AlertHistory.AlertLevel.HIGH);
        request.setMessage(String.format(
                "项目 %s 费用已达到预算的 %.2f%% (已用: %s, 预算: %s)",
                project.getName(),
                expenseRatio,
                totalExpense,
                budget
        ));
        request.setRelatedId(String.format("Project:%s", project.getId()));
        AlertHistoryCreateResult result = alertHistoryService.createAlertHistoryIfAbsent(request);
        // 仅在新建告警时触发通知，复用已有未处理告警不重复推送
        if (result.created()) {
            alertNotificationOrchestrator.dispatchNotification(
                    result.alertHistory(), rule,
                    buildBudgetPayload(project, expenseRatio, totalExpense, budget));
        }
    }

    /**
     * 构建预算告警附加载荷，供通知服务在跳转和展示时使用。
     *
     * @param project      项目实体
     * @param expenseRatio 费用占比（百分比）
     * @param totalExpense 已用费用
     * @param budget       预算
     * @return payload map，包含 projectId/projectName/expenseRatio/totalExpense/budget/targetUrl
     */
    private Map<String, Object> buildBudgetPayload(Project project, BigDecimal expenseRatio,
                                                   BigDecimal totalExpense, BigDecimal budget) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("projectId", project.getId());
        payload.put("projectName", project.getName());
        payload.put("expenseRatio", expenseRatio);
        payload.put("totalExpense", totalExpense);
        payload.put("budget", budget);
        payload.put("targetUrl", "/projects/" + project.getId());
        return payload;
    }
}
