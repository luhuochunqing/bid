package com.xiyu.bid.resources.expenseledger.application;

import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.resources.dto.ExpenseDTO;
import com.xiyu.bid.resources.dto.ResourceResponseMapper;
import com.xiyu.bid.resources.entity.Expense;
import com.xiyu.bid.resources.expenseledger.domain.ExpenseLedgerStatisticsCalculator;
import com.xiyu.bid.resources.expenseledger.dto.ExpenseLedgerItemDTO;
import com.xiyu.bid.resources.expenseledger.dto.ExpenseLedgerQuery;
import com.xiyu.bid.resources.expenseledger.dto.ExpenseLedgerResponse;
import com.xiyu.bid.resources.repository.ExpenseRepository;
import com.xiyu.bid.resources.service.expense.ExpenseAccessGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExpenseLedgerApplicationService {

    private final ExpenseRepository expenseRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final ExpenseLedgerStatisticsCalculator statisticsCalculator;
    private final ExpenseAccessGuard accessGuard;

    public ExpenseLedgerResponse queryLedger(ExpenseLedgerQuery query) {
        validate(query);

        if (query.getProjectId() != null) {
            accessGuard.assertCanAccessProject(query.getProjectId());
        }
        List<Expense> expenses = loadVisibleExpenses();
        Map<Long, Project> projectsById = loadProjects(expenses);
        Map<Long, User> managersById = loadManagers(projectsById.values());

        List<ExpenseLedgerItemDTO> items = expenses.stream()
                .map(expense -> toLedgerItem(expense, projectsById.get(expense.getProjectId()), managersById))
                .filter(item -> matches(item, query))
                .toList();

        return ExpenseLedgerResponse.builder()
                .items(items)
                .summary(statisticsCalculator.summarize(items))
                .build();
    }

    private List<Expense> loadVisibleExpenses() {
        Sort sort = Sort.by(Sort.Direction.DESC, "date", "createdAt");
        List<Long> visibleProjectIds = accessGuard.visibleProjectIdsForCurrentUser();
        if (visibleProjectIds == null) {
            return expenseRepository.findAll(sort);
        }
        if (visibleProjectIds.isEmpty()) {
            return List.of();
        }
        return expenseRepository.findByProjectIdIn(visibleProjectIds, sort);
    }

    private Map<Long, Project> loadProjects(List<Expense> expenses) {
        Set<Long> projectIds = expenses.stream()
                .map(Expense::getProjectId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        return projectRepository.findAllById(projectIds).stream()
                .collect(Collectors.toMap(Project::getId, Function.identity()));
    }

    private Map<Long, User> loadManagers(Iterable<Project> projects) {
        Set<Long> managerIds = java.util.stream.StreamSupport.stream(projects.spliterator(), false)
                .map(Project::getManagerId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        return userRepository.findAllById(managerIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
    }

    private ExpenseLedgerItemDTO toLedgerItem(Expense expense, Project project, Map<Long, User> managersById) {
        ExpenseDTO base = ResourceResponseMapper.toDto(expense);
        User manager = project == null ? null : managersById.get(project.getManagerId());

        return ExpenseLedgerItemDTO.builder()
                .id(base.getId())
                .projectId(base.getProjectId())
                .projectName(project == null ? "项目#" + base.getProjectId() : project.getName())
                .departmentCode(manager == null ? "UNASSIGNED" : normalize(manager.getDepartmentCode(), "UNASSIGNED"))
                .departmentName(manager == null ? "未分配部门" : normalize(manager.getDepartmentName(), "未分配部门"))
                .category(base.getCategory())
                .expenseType(base.getExpenseType())
                .amount(base.getAmount())
                .date(base.getDate())
                .description(base.getDescription())
                .createdBy(base.getCreatedBy())
                .status(base.getStatus())
                .approvalComment(base.getApprovalComment())
                .approvedBy(base.getApprovedBy())
                .approvedAt(base.getApprovedAt())
                .returnRequestedAt(base.getReturnRequestedAt())
                .returnConfirmedAt(base.getReturnConfirmedAt())
                .returnComment(base.getReturnComment())
                .createdAt(base.getCreatedAt())
                .updatedAt(base.getUpdatedAt())
                .build();
    }

    private boolean matches(ExpenseLedgerItemDTO item, ExpenseLedgerQuery query) {
        if (query.getProjectId() != null && !query.getProjectId().equals(item.getProjectId())) {
            return false;
        }
        if (hasText(query.getProjectKeyword())
                && !containsIgnoreCase(item.getProjectName(), query.getProjectKeyword())) {
            return false;
        }
        if (query.getStartDate() != null
                && (item.getDate() == null || item.getDate().isBefore(query.getStartDate()))) {
            return false;
        }
        if (query.getEndDate() != null
                && (item.getDate() == null || item.getDate().isAfter(query.getEndDate()))) {
            return false;
        }
        if (hasText(query.getDepartment())
                && !(containsIgnoreCase(item.getDepartmentCode(), query.getDepartment())
                || containsIgnoreCase(item.getDepartmentName(), query.getDepartment()))) {
            return false;
        }
        if (hasText(query.getExpenseType()) && !query.getExpenseType().equals(item.getExpenseType())) {
            return false;
        }
        return query.getStatus() == null || query.getStatus().name().equals(item.getStatus().name());
    }

    private void validate(ExpenseLedgerQuery query) {
        if (query.getStartDate() != null
                && query.getEndDate() != null
                && query.getStartDate().isAfter(query.getEndDate())) {
            throw new IllegalArgumentException("Start date cannot be after end date");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private boolean containsIgnoreCase(String source, String keyword) {
        if (source == null || keyword == null) {
            return false;
        }
        return source.toLowerCase(java.util.Locale.ROOT).contains(keyword.trim().toLowerCase(java.util.Locale.ROOT));
    }

    private String normalize(String value, String fallback) {
        return hasText(value) ? value.trim() : fallback;
    }
}
