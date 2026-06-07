package com.xiyu.bid.task.core;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Pure core policy for deliverable-task association rules.
 * No state, no dependencies, no side effects.
 */
public final class DeliverableAssociationPolicy {

    /** Maximum deliverables allowed per single task. */
    private static final int MAX_DELIVERABLES_PER_TASK = 20;

    /** Full coverage percentage constant. */
    private static final double FULL_COVERAGE_PCT = 100.0;

    private DeliverableAssociationPolicy() {
    }

    /**
     * Validate whether a deliverable can be associated.
     *
     * @param taskStatus    task status string from entity
     * @param type          deliverable type to associate
     * @param existingCount current deliverable count for this task
     * @return association validation result
     */
    public static AssociationResult validateAssociation(
            final String taskStatus,
            final DeliverableType type,
            final int existingCount) {

        if (taskStatus == null) {
            return AssociationResult.denied("任务状态未知");
        }

        // Cannot associate to terminal or cancelled tasks
        if ("COMPLETED".equals(taskStatus)
                || "CANCELLED".equals(taskStatus)) {
            return AssociationResult.denied(
                    "已完成或已取消的任务不可关联交付物");
        }

        if (type == null) {
            return AssociationResult.denied("交付物类型不能为空");
        }

        if (existingCount >= MAX_DELIVERABLES_PER_TASK) {
            return AssociationResult.denied(
                    "单任务交付物数量已达上限("
                    + MAX_DELIVERABLES_PER_TASK + ")");
        }

        return AssociationResult.ok();
    }

    /**
     * Compute coverage fraction of required types already present.
     *
     * @param requiredTypes list of required type name strings
     * @param actualTypes   list of actual deliverable types
     * @return completion coverage summary
     */
    public static CompletionCoverage computeCompletionCoverage(
            final List<String> requiredTypes,
            final List<DeliverableType> actualTypes) {

        if (requiredTypes == null || requiredTypes.isEmpty()) {
            return new CompletionCoverage(0, 0,
                    FULL_COVERAGE_PCT, List.of());
        }

        Set<DeliverableType> actualSet = actualTypes == null
                ? Set.of()
                : actualTypes.stream().collect(Collectors.toSet());

        final int[] coveredHolder = {0};
        var typeCoverages = requiredTypes.stream().map(req -> {
            DeliverableType dt = parseType(req);
            boolean found = actualSet.contains(dt);
            if (found) {
                coveredHolder[0]++;
            }
            return new TypeCoverage(
                    req, labelFor(dt), found,
                    countOfType(actualTypes, dt));
        }).toList();
        int covered = coveredHolder[0];

        double pct = requiredTypes.isEmpty()
                ? FULL_COVERAGE_PCT
                : (covered * FULL_COVERAGE_PCT / requiredTypes.size());
        return new CompletionCoverage(
                requiredTypes.size(), covered, pct, typeCoverages);
    }

    private static DeliverableType parseType(final String value) {
        if (value == null) {
            return DeliverableType.OTHER;
        }
        try {
            return DeliverableType.valueOf(
                    value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return DeliverableType.OTHER;
        }
    }

    private static String labelFor(final DeliverableType type) {
        if (type == null) {
            return "其他";
        }
        return switch (type) {
            case DOCUMENT -> "文档";
            case QUALIFICATION -> "资质文件";
            case TECHNICAL -> "技术方案";
            case QUOTATION -> "报价单";
            case OTHER -> "其他";
        };
    }

    private static int countOfType(
            final List<DeliverableType> types,
            final DeliverableType target) {
        if (types == null) {
            return 0;
        }
        int count = 0;
        for (DeliverableType t : types) {
            if (t == target) {
                count++;
            }
        }
        return count;
    }

    /**
     * Result of an association validation.
     *
     * @param valid           whether association is allowed
     * @param rejectionReason reason if denied, empty if accepted
     */
    public record AssociationResult(
            boolean valid, String rejectionReason) {

        /** Create an accepted result.
         *
         * @return accepted association result
         */
        public static AssociationResult ok() {
            return new AssociationResult(true, "");
        }

        /** Create a denied result with reason.
         *
         * @param reason the rejection reason
         * @return denied association result
         */
        public static AssociationResult denied(final String reason) {
            return new AssociationResult(false, reason);
        }
    }

    /**
     * Coverage computation result.
     *
     * @param required      number of required types
     * @param covered       number of types with deliverables
     * @param percentage    coverage as 0-100 value
     * @param typeCoverages per-type breakdown
     */
    public record CompletionCoverage(
            int required,
            int covered,
            double percentage,
            List<TypeCoverage> typeCoverages) {
        public CompletionCoverage {
            typeCoverages = typeCoverages == null ? List.of() : List.copyOf(typeCoverages);
        }
    }

    /**
     * Per-type coverage detail.
     *
     * @param type    type enum name
     * @param label   human-readable label
     * @param covered whether this type has a deliverable
     * @param count   number of deliverables of this type
     */
    public record TypeCoverage(
            String type, String label,
            boolean covered, int count) {
    }

    /**
     * Deliverable type taxonomy matching frontend dropdown options.
     */
    public enum DeliverableType {
        /** General document. */
        DOCUMENT,
        /** Qualification certificate. */
        QUALIFICATION,
        /** Technical proposal. */
        TECHNICAL,
        /** Price quotation. */
        QUOTATION,
        /** Other/miscellaneous. */
        OTHER
    }
}
