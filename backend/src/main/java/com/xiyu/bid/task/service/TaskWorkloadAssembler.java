package com.xiyu.bid.task.service;

import com.xiyu.bid.entity.Task;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.task.dto.TeamTaskWorkloadDTO;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

final class TaskWorkloadAssembler {

    private static final DateTimeFormatter DISPLAY_DATE_TIME = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    private TaskWorkloadAssembler() {
    }

    static TeamTaskWorkloadDTO.TeamMemberWorkloadDTO buildTeamMemberWorkload(User user, List<Task> tasks) {
        long todoCount = tasks.stream().filter(task -> task.getStatus() == Task.Status.TODO).count();
        long inProgressCount = tasks.stream().filter(task -> task.getStatus() == Task.Status.IN_PROGRESS).count();
        long overdueCount = tasks.stream().filter(TaskWorkloadAssembler::isOverdue).count();
        LocalDateTime weekStart = LocalDateTime.now().minusDays(7);
        long completedThisWeekCount = tasks.stream()
                .filter(task -> task.getStatus() == Task.Status.COMPLETED)
                .filter(task -> task.getUpdatedAt() != null && !task.getUpdatedAt().isBefore(weekStart))
                .count();

        int workloadScore = tasks.stream().mapToInt(TaskWorkloadAssembler::calculateWorkloadScore).sum();
        String workloadLevel = workloadScore >= 10 ? "high" : workloadScore >= 5 ? "medium" : "low";

        List<TeamTaskWorkloadDTO.MemberTaskSummaryDTO> summaries = tasks.stream()
                .sorted(Comparator
                        .comparing((Task task) -> !isOverdue(task))
                        .thenComparing((Task task) -> !isDueSoon(task))
                        .thenComparing((Task task) -> task.getPriority() == null ? Integer.MAX_VALUE : task.getPriority().ordinal())
                        .thenComparing(Task::getDueDate, Comparator.nullsLast(Comparator.naturalOrder())))
                .limit(3)
                .map(task -> TeamTaskWorkloadDTO.MemberTaskSummaryDTO.builder()
                        .id(task.getId())
                        .title(task.getTitle())
                        .priority(task.getPriority() == null ? "MEDIUM" : task.getPriority().name())
                        .status(task.getStatus() == null ? "TODO" : task.getStatus().name())
                        .deadline(task.getDueDate() == null ? "待排期" : task.getDueDate().format(DISPLAY_DATE_TIME))
                        .overdue(isOverdue(task))
                        .dueSoon(isDueSoon(task))
                        .build())
                .toList();

        return TeamTaskWorkloadDTO.TeamMemberWorkloadDTO.builder()
                .userId(user.getId())
                .name(user.getFullName())
                .roleCode(user.getRoleCode())
                .roleName(user.getRoleName())
                .deptCode(defaultText(user.getDepartmentCode(), "UNASSIGNED"))
                .deptName(defaultText(user.getDepartmentName(), "未配置部门"))
                .todoCount(todoCount)
                .inProgressCount(inProgressCount)
                .overdueCount(overdueCount)
                .completedThisWeekCount(completedThisWeekCount)
                .workloadScore(workloadScore)
                .workloadLevel(workloadLevel)
                .tasks(summaries)
                .build();
    }

    private static int calculateWorkloadScore(Task task) {
        int score = switch (task.getPriority() == null ? Task.Priority.MEDIUM : task.getPriority()) {
            case URGENT, HIGH -> 3;
            case MEDIUM -> 2;
            case LOW -> 1;
        };
        if (isOverdue(task)) {
            score += 2;
        }
        if (isDueSoon(task)) {
            score += 1;
        }
        return score;
    }

    private static boolean isOverdue(Task task) {
        return task.getDueDate() != null
                && task.getDueDate().isBefore(LocalDateTime.now())
                && task.getStatus() != Task.Status.COMPLETED;
    }

    private static boolean isDueSoon(Task task) {
        return task.getDueDate() != null
                && !isOverdue(task)
                && task.getDueDate().isBefore(LocalDateTime.now().plusHours(48));
    }

    private static String defaultText(String value, String fallback) {
        return value != null && !value.isBlank() ? value.trim() : fallback;
    }
}
