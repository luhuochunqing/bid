package com.xiyu.bid.task.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO representing deliverable coverage for a task.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeliverableCoverageDTO {

    /** Task id being covered. */
    private Long taskId;

    /** Number of required deliverable types. */
    private int requiredCount;

    /** Number of types that have at least one deliverable. */
    private int coveredCount;

    /** Coverage percentage 0-100. */
    private double percentage;

    /** Per-type coverage breakdown. */
    private List<TypeCoverage> typeCoverages;

    /**
     * Per-type coverage detail.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TypeCoverage {

        /** Deliverable type enum name. */
        private String type;

        /** Human-readable label. */
        private String label;

        /** Whether this type has at least one deliverable. */
        private boolean covered;

        /** Number of deliverables of this type. */
        private int count;
    }
}
