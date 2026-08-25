// Input: AlertRule（BUDGET 类型）+ 活跃项目列表/标讯预算/项目费用汇总（批量预加载防 N+1）
// Output: 预算费用告警历史 + 通知分发（经 AlertNotificationOrchestrator.createAndNotifyIfNew）
// Pos: alertdispatch 模块预算告警扫描（AlertSchedulerService 每日 09:00 调度入口）
// 维护声明: 单项目告警创建失败按 P1-7 吞异常续跑整批，但必须带堆栈记日志并上报 Sentry（CO-564 纪律）；
//           新增告警规则类型在 AlertRuleDispatchService 分发，勿在本类扩 switch。
package com.xiyu.bid.alertdispatch.service;

import com.xiyu.bid.alerts.dto.AlertHistoryCreateRequest;
import com.xiyu.bid.alerts.entity.AlertHistory;
import com.xiyu.bid.alerts.entity.AlertRule;
import com.xiyu.bid.alerts.service.AlertNotificationOrchestrator;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.resources.repository.ExpenseRepository;
import io.sentry.Sentry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
@RequiredArgsConstructor
public class BudgetAlertDispatchService {

    private final AlertNotificationOrchestrator alertNotificationOrchestrator;
    private final ProjectRepository projectRepository;
    private final TenderRepository tenderRepository;
    private final ExpenseRepository expenseRepository;

    private static final Logger logger = LoggerFactory.getLogger(BudgetAlertDispatchService.class);

    public void dispatch(AlertRule rule) {
        List<Project> activeProjects = projectRepository.findActiveProjects();
        if (activeProjects.isEmpty()) {
            return;
        }

        // P0-2: 批量加载 Tender 和 Expense 汇总，避免循环内 N+1 查询
        Set<Long> tenderIds = activeProjects.stream()
                .map(Project::getTenderId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());
        Map<Long, Tender> tenderMap = tenderIds.isEmpty()
                ? Map.of()
                : tenderRepository.findAllById(tenderIds).stream()
                        .collect(Collectors.toMap(Tender::getId, Function.identity(), (a, b) -> a));

        Set<Long> projectIds = activeProjects.stream()
                .map(Project::getId)
                .collect(Collectors.toSet());
        Map<Long, BigDecimal> expenseMap = batchLoadExpenseSums(projectIds);

        for (Project project : activeProjects) {
            Tender tender = tenderMap.get(project.getTenderId());
            if (tender == null || tender.getBudget() == null || tender.getBudget().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            BigDecimal totalExpense = expenseMap.getOrDefault(project.getId(), BigDecimal.ZERO);
            BigDecimal expenseRatio = totalExpense
                    .divide(tender.getBudget(), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));

            if (shouldAlert(rule, expenseRatio)) {
                try {
                    createAlert(rule, project, expenseRatio, totalExpense, tender.getBudget());
                } catch (RuntimeException e) {
                    // P1-7: 单项目异常不应导致整批通知丢失，记录后继续处理下一个项目。
                    // CO-564 同款纪律：吞异常必须带堆栈并上报 Sentry，让运维可主动发现（静默只打 message
                    // 会让 DB 级故障表现为"当天告警全部消失"而无人知晓）。
                    logger.error("项目 {} 费用告警创建失败，已跳过", project.getId(), e);
                    Sentry.captureException(e);
                }
            }
        }
    }

    /**
     * P0-2: 批量查询项目费用总额，返回 projectId → totalAmount 映射。
     */
    private Map<Long, BigDecimal> batchLoadExpenseSums(Set<Long> projectIds) {
        if (projectIds.isEmpty()) {
            return Map.of();
        }
        List<Object[]> rows = expenseRepository.sumAmountByProjectIdIn(List.copyOf(projectIds));
        Map<Long, BigDecimal> result = new HashMap<>();
        for (Object[] row : rows) {
            Long projectId = (Long) row[0];
            BigDecimal total = (BigDecimal) row[1];
            result.put(projectId, total != null ? total : BigDecimal.ZERO);
        }
        return result;
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
        // P1-3: 使用 createAndNotifyIfNew 模板方法，消除 create + dispatch 重复
        alertNotificationOrchestrator.createAndNotifyIfNew(request, rule,
                buildBudgetPayload(project, expenseRatio, totalExpense, budget));
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
