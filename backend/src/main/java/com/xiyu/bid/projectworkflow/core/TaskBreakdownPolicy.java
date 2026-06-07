package com.xiyu.bid.projectworkflow.core;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Pure task breakdown rules from tender requirement or section snapshots.
 */
public final class TaskBreakdownPolicy {

    private static final int DEFAULT_DUE_DAYS_BEFORE_DEADLINE = 5;

    private TaskBreakdownPolicy() {
    }

    public static Decision decide(Command command) {
        Set<String> existingTitles = new HashSet<>();
        command.existingTasks().stream()
                .map(ExistingTaskSnapshot::title)
                .map(TaskBreakdownPolicy::normalizeKey)
                .filter(value -> !value.isBlank())
                .forEach(existingTitles::add);

        List<GeneratedTask> generatedTasks = command.sources().stream()
                .map(source -> toGeneratedTask(source, command.projectDeadline()))
                .filter(task -> !task.title().isBlank())
                .filter(task -> existingTitles.add(normalizeKey(task.title())))
                .toList();
        return new Decision(generatedTasks);
    }

    private static GeneratedTask toGeneratedTask(SourceSnapshot source, LocalDate projectDeadline) {
        BreakdownKind kind = resolveKind(source);
        String title = normalizeText(source.title());
        if (title.isBlank()) {
            return new GeneratedTask("", "", kind.priority, null, kind.deliverableType);
        }
        return new GeneratedTask(
                kind.prefix + title,
                buildDescription(kind, source),
                kind.priority,
                resolveDueDate(projectDeadline),
                kind.deliverableType
        );
    }

    private static BreakdownKind resolveKind(SourceSnapshot source) {
        String haystack = (normalizeText(source.category()) + " " + normalizeText(source.title()) + " "
                + normalizeText(source.content())).toLowerCase(Locale.ROOT);
        if (containsAny(haystack, "technical", "技术", "方案", "实施", "接口", "对接")) {
            return BreakdownKind.TECHNICAL;
        }
        if (containsAny(haystack, "commercial", "business", "商务", "偏离", "条款", "合同")) {
            return BreakdownKind.BUSINESS;
        }
        if (containsAny(haystack, "material", "qualification", "资格", "资质", "材料", "证明", "证书")) {
            return BreakdownKind.MATERIAL;
        }
        if (containsAny(haystack, "scoring", "score", "评分", "评审", "得分")) {
            return BreakdownKind.SCORING;
        }
        return BreakdownKind.REVIEW;
    }

    private static boolean containsAny(String value, String... keywords) {
        for (String keyword : keywords) {
            if (value.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private static String buildDescription(BreakdownKind kind, SourceSnapshot source) {
        String content = normalizeText(source.content());
        String title = normalizeText(source.title());
        String basis = content.isBlank() ? title : content;
        return kind.descriptionPrefix + basis;
    }

    private static LocalDate resolveDueDate(LocalDate projectDeadline) {
        return projectDeadline == null ? null : projectDeadline.minusDays(DEFAULT_DUE_DAYS_BEFORE_DEADLINE);
    }

    private static String normalizeText(String value) {
        return value == null ? "" : value.trim();
    }

    private static String normalizeKey(String value) {
        return normalizeText(value).replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    private enum BreakdownKind {
        BUSINESS("商务标：", "根据商务标要求准备：", TaskPriority.HIGH, "commercial_response"),
        TECHNICAL("技术标：", "根据技术标要求准备：", TaskPriority.HIGH, "technical_plan"),
        MATERIAL("资料收集：", "根据资格/材料要求准备：", TaskPriority.MEDIUM, "qualification"),
        SCORING("评分复核：", "根据评分点复核响应覆盖：", TaskPriority.MEDIUM, "review"),
        REVIEW("任务复核：", "根据标书拆解结果复核：", TaskPriority.MEDIUM, "other");

        private final String prefix;
        private final String descriptionPrefix;
        private final TaskPriority priority;
        private final String deliverableType;

        BreakdownKind(
                String prefix,
                String descriptionPrefix,
                TaskPriority priority,
                String deliverableType
        ) {
            this.prefix = prefix;
            this.descriptionPrefix = descriptionPrefix;
            this.priority = priority;
            this.deliverableType = deliverableType;
        }
    }

    public record Command(
            LocalDate projectDeadline,
            List<SourceSnapshot> sources,
            List<ExistingTaskSnapshot> existingTasks
    ) {
        public Command {
            sources = sources == null ? List.of() : List.copyOf(sources);
            existingTasks = existingTasks == null ? List.of() : List.copyOf(existingTasks);
        }
    }

    public record SourceSnapshot(String category, String title, String content) {
    }

    public record ExistingTaskSnapshot(String title) {
    }

    public record GeneratedTask(
            String title,
            String description,
            TaskPriority priority,
            LocalDate dueDate,
            String deliverableType
    ) {
    }

    public record Decision(List<GeneratedTask> tasks) {
        public Decision {
            tasks = tasks == null ? List.of() : List.copyOf(tasks);
        }
    }

    public enum TaskPriority {
        LOW,
        MEDIUM,
        HIGH,
        URGENT
    }
}
