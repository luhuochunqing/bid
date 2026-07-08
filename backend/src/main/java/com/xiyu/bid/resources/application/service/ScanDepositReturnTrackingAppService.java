package com.xiyu.bid.resources.application.service;

import com.xiyu.bid.alerts.dto.AlertHistoryCreateRequest;
import com.xiyu.bid.alerts.dto.AlertHistoryCreateResult;
import com.xiyu.bid.alerts.entity.AlertHistory;
import com.xiyu.bid.alerts.entity.AlertRule;
import com.xiyu.bid.alerts.service.AlertNotificationOrchestrator;
import com.xiyu.bid.alerts.service.AlertRuleProvisioningService;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ScanDepositReturnTrackingAppService {

    private final ExpenseRepository expenseRepository;
    private final BidResultFetchResultRepository bidResultFetchResultRepository;
    private final AlertRuleProvisioningService alertRuleProvisioningService;
    private final AlertNotificationOrchestrator alertNotificationOrchestrator;
    private final SettingsService settingsService;
    private final ProjectRepository projectRepository;

    private final DepositReturnReminderPolicy reminderPolicy = new DepositReturnReminderPolicy();

    @Transactional
    public int scan() {
        int warnDays = Optional.ofNullable(settingsService.getSettings().getSystemConfig())
                .map(config -> config.getDepositWarnDays())
                .filter(value -> value != null && value > 0)
                .orElse(7);
        // P1-4: 使用共享的 AlertRuleProvisioningService.ensureRuleWithThresholdSync
        AlertRule rule = alertRuleProvisioningService.ensureRuleWithThresholdSync(
                AlertRule.AlertType.DEPOSIT_RETURN, "保证金退还提醒", warnDays);
        List<Expense> expenses = expenseRepository
                .findByExpenseTypeAndExpectedReturnDateIsNotNullAndStatusNotOrderByExpectedReturnDateAsc(
                        "保证金",
                        Expense.ExpenseStatus.RETURNED);
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();
        int reminded = 0;

        // P1-6: 批量预加载关联数据，消除循环内 N+1 查询
        Map<Long, BidResultFetchResult> resultMap = batchLoadBidResults(expenses);
        Map<Long, String> projectNameMap = batchLoadProjectNames(expenses);

        for (Expense expense : expenses) {
            BidResultFetchResult result = resultMap.get(expense.getProjectId());

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

            String projectName = projectNameMap.getOrDefault(
                    expense.getProjectId(), "项目#" + expense.getProjectId());
            AlertHistoryCreateRequest request = new AlertHistoryCreateRequest();
            request.setRuleId(rule.getId());
            request.setLevel(decision.stage() == com.xiyu.bid.resources.domain.valueobject.DepositReturnReminderStage.OVERDUE
                    ? AlertHistory.AlertLevel.HIGH
                    : AlertHistory.AlertLevel.MEDIUM);
            request.setRelatedId(decision.relatedId(expense.getId(), expense.getExpectedReturnDate().toString()));
            request.setMessage(buildReminderMessage(expense, result, decision, projectName));
            // P1-3: 使用 createAndNotifyIfNew 模板方法，消除 create + dispatch 重复
            AlertHistoryCreateResult alertResult = alertNotificationOrchestrator.createAndNotifyIfNew(
                    request, rule, buildDepositPayload(expense, projectName));

            // P1-11: 副作用仅在新建告警时执行 — 避免重复提醒时也更新 reminder 时间和计数
            if (alertResult.created()) {
                expense.recordReturnReminder(now);
                expenseRepository.save(expense);
                reminded++;
            }
        }

        return reminded;
    }

    /**
     * P1-6: 批量加载 expenses 关联的 BidResultFetchResult，避免循环内 N+1 查询。
     *
     * <p>按 projectId 分组取最新一条 CONFIRMED 记录（结果已按 confirmedAt DESC, fetchTime DESC 排序）。</p>
     */
    private Map<Long, BidResultFetchResult> batchLoadBidResults(List<Expense> expenses) {
        Set<Long> projectIds = expenses.stream()
                .map(Expense::getProjectId)
                .collect(Collectors.toSet());
        if (projectIds.isEmpty()) {
            return Map.of();
        }
        List<BidResultFetchResult> results = bidResultFetchResultRepository
                .findByProjectIdsInAndStatus(projectIds, BidResultFetchResult.Status.CONFIRMED);
        // 按 projectId 分组，取每组的第一个（即最新的一条）
        return results.stream()
                .collect(Collectors.groupingBy(BidResultFetchResult::getProjectId))
                .entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().get(0),
                        (a, b) -> a));
    }

    /**
     * P1-6: 批量加载 expenses 关联的 Project 名称，避免循环内 N+1 查询。
     */
    private Map<Long, String> batchLoadProjectNames(List<Expense> expenses) {
        Set<Long> projectIds = expenses.stream()
                .map(Expense::getProjectId)
                .collect(Collectors.toSet());
        if (projectIds.isEmpty()) {
            return Map.of();
        }
        return projectRepository.findAllById(projectIds).stream()
                .collect(Collectors.toMap(Project::getId, Project::getName, (a, b) -> a));
    }

    private String buildReminderMessage(
            Expense expense,
            BidResultFetchResult result,
            DepositReturnReminderDecision decision,
            String projectName
    ) {
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

    /**
     * 构建保证金退还通知的附加 payload。
     *
     * <p>供 {@link AlertNotificationOrchestrator#createAndNotifyIfNew} 使用，
     * 携带跳转到保证金退还跟踪页所需的关键业务字段。</p>
     */
    private Map<String, Object> buildDepositPayload(Expense expense, String projectName) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("expenseId", expense.getId());
        payload.put("projectId", expense.getProjectId());
        payload.put("projectName", projectName);
        payload.put("expectedReturnDate", expense.getExpectedReturnDate());
        payload.put("targetUrl", "/resources/deposit-return-tracking");
        return payload;
    }
}
