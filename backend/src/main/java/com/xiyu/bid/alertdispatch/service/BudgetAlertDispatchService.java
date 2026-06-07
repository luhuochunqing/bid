package com.xiyu.bid.alertdispatch.service;

import com.xiyu.bid.alerts.dto.AlertHistoryCreateRequest;
import com.xiyu.bid.alerts.entity.AlertHistory;
import com.xiyu.bid.alerts.entity.AlertRule;
import com.xiyu.bid.alerts.service.AlertHistoryService;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.resources.repository.ExpenseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
public class BudgetAlertDispatchService {

    private final AlertHistoryService alertHistoryService;
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
        alertHistoryService.createAlertHistory(request);
    }
}
