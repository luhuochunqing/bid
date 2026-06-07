package com.xiyu.bid.resources.application.service;

import com.xiyu.bid.alerts.dto.AlertHistoryCreateRequest;
import com.xiyu.bid.alerts.entity.AlertHistory;
import com.xiyu.bid.alerts.entity.AlertRule;
import com.xiyu.bid.alerts.repository.AlertRuleRepository;
import com.xiyu.bid.alerts.service.AlertHistoryService;
import com.xiyu.bid.bidresult.entity.BidResultFetchResult;
import com.xiyu.bid.bidresult.repository.BidResultFetchResultRepository;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.resources.domain.model.DepositReturnReminderDecision;
import com.xiyu.bid.resources.domain.model.DepositReturnTrackingSnapshot;
import com.xiyu.bid.resources.domain.service.DepositReturnReminderPolicy;
import com.xiyu.bid.resources.entity.Expense;
import com.xiyu.bid.resources.repository.ExpenseRepository;
import com.xiyu.bid.settings.service.SettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ScanDepositReturnTrackingAppService {

    private final ExpenseRepository expenseRepository;
    private final BidResultFetchResultRepository bidResultFetchResultRepository;
    private final AlertRuleRepository alertRuleRepository;
    private final AlertHistoryService alertHistoryService;
    private final SettingsService settingsService;
    private final ProjectRepository projectRepository;

    private final DepositReturnReminderPolicy reminderPolicy = new DepositReturnReminderPolicy();

    @Transactional
    public int scan() {
        int warnDays = Optional.ofNullable(settingsService.getSettings().getSystemConfig())
                .map(config -> config.getDepositWarnDays())
                .filter(value -> value != null && value > 0)
                .orElse(7);
        AlertRule rule = ensureAlertRule(warnDays);
        List<Expense> expenses = expenseRepository
                .findByExpenseTypeAndExpectedReturnDateIsNotNullAndStatusNotOrderByExpectedReturnDateAsc(
                        "保证金",
                        Expense.ExpenseStatus.RETURNED);
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();
        int reminded = 0;

        for (Expense expense : expenses) {
            BidResultFetchResult result = bidResultFetchResultRepository
                    .findFirstByProjectIdAndStatusOrderByConfirmedAtDescFetchTimeDesc(
                            expense.getProjectId(), BidResultFetchResult.Status.CONFIRMED)
                    .orElse(null);

            DepositReturnReminderDecision decision = reminderPolicy.evaluate(
                    new DepositReturnTrackingSnapshot(
                            expense.getId(),
                            expense.getProjectId(),
                            expense.getStatus(),
                            expense.getExpectedReturnDate(),
                            expense.getLastReturnReminderAt(),
                            result == null ? null : result.getResult()
                    ),
                    warnDays,
                    today,
                    now
            );

            if (!decision.shouldRemind()) {
                continue;
            }

            AlertHistoryCreateRequest request = new AlertHistoryCreateRequest();
            request.setRuleId(rule.getId());
            request.setLevel(decision.stage() == com.xiyu.bid.resources.domain.valueobject.DepositReturnReminderStage.OVERDUE
                    ? AlertHistory.AlertLevel.HIGH
                    : AlertHistory.AlertLevel.MEDIUM);
            request.setRelatedId(decision.relatedId(expense.getId(), expense.getExpectedReturnDate().toString()));
            request.setMessage(buildReminderMessage(expense, result, decision));
            alertHistoryService.createAlertHistory(request);
            expense.recordReturnReminder(now);
            expenseRepository.save(expense);
            reminded++;
        }

        return reminded;
    }

    private AlertRule ensureAlertRule(int warnDays) {
        return alertRuleRepository.findByType(AlertRule.AlertType.DEPOSIT_RETURN).stream()
                .findFirst()
                .map(rule -> syncRuleThreshold(rule, warnDays))
                .orElseGet(() -> alertRuleRepository.save(AlertRule.builder()
                        .name("保证金退还提醒")
                        .type(AlertRule.AlertType.DEPOSIT_RETURN)
                        .condition(AlertRule.ConditionType.LESS_THAN)
                        .threshold(BigDecimal.valueOf(warnDays))
                        .enabled(true)
                        .createdBy("system")
                        .build()));
    }

    private String buildReminderMessage(
            Expense expense,
            BidResultFetchResult result,
            DepositReturnReminderDecision decision
    ) {
        String projectName = projectRepository.findById(expense.getProjectId())
                .map(Project::getName)
                .orElse("项目#" + expense.getProjectId());
        String resultText = result == null ? "待确认" : (result.getResult() == BidResultFetchResult.Result.WON ? "中标" : "未中标");
        if (decision.stage() == com.xiyu.bid.resources.domain.valueobject.DepositReturnReminderStage.OVERDUE) {
            return String.format(
                    "%s（%s）的保证金已逾期 %d 天未退还，应退日期 %s",
                    projectName,
                    resultText,
                    decision.overdueDays(),
                    expense.getExpectedReturnDate()
            );
        }
        return String.format(
                "%s（%s）的保证金将于 %d 天后到期退还，应退日期 %s",
                projectName,
                resultText,
                decision.daysUntilDue(),
                expense.getExpectedReturnDate()
        );
    }

    private AlertRule syncRuleThreshold(AlertRule rule, int warnDays) {
        if (rule.getThreshold() != null
                && rule.getThreshold().compareTo(BigDecimal.valueOf(warnDays)) == 0
                && rule.getCondition() == AlertRule.ConditionType.LESS_THAN
                && rule.getEnabled()) {
            return rule;
        }
        rule.setThreshold(BigDecimal.valueOf(warnDays));
        rule.setCondition(AlertRule.ConditionType.LESS_THAN);
        rule.setEnabled(true);
        return alertRuleRepository.save(rule);
    }
}
