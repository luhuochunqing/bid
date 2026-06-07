package com.xiyu.bid.marketinsight.core;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Pure core policy for prediction status transition validation.
 * No state, no dependencies, no side effects.
 */
public final class PredictionTransitionPolicy {

    /** Allowed transitions from each status. */
    private static final Map<PredictionStatus, Set<PredictionStatus>>
            ALLOWED_TRANSITIONS;

    static {
        EnumMap<PredictionStatus, Set<PredictionStatus>> map =
                new EnumMap<>(PredictionStatus.class);
        map.put(PredictionStatus.WATCH,
                Set.of(PredictionStatus.RECOMMEND,
                        PredictionStatus.CANCELLED));
        map.put(PredictionStatus.RECOMMEND,
                Set.of(PredictionStatus.CONVERTED,
                        PredictionStatus.WATCH,
                        PredictionStatus.CANCELLED));
        map.put(PredictionStatus.CONVERTED, Set.of());
        map.put(PredictionStatus.CANCELLED,
                Set.of(PredictionStatus.WATCH));
        ALLOWED_TRANSITIONS = Map.copyOf(map);
    }

    private PredictionTransitionPolicy() {
    }

    /**
     * Validate whether a prediction status transition is legal.
     *
     * @param current current prediction status
     * @param target  target prediction status
     * @return TransitionResult with allowed flag and reason
     */
    public static TransitionResult validateTransition(
            final PredictionStatus current,
            final PredictionStatus target) {
        if (current == null || target == null) {
            return TransitionResult.denied("状态不能为空");
        }
        if (current == target) {
            return TransitionResult.ok();
        }
        Set<PredictionStatus> allowed =
                ALLOWED_TRANSITIONS.getOrDefault(current, Set.of());
        if (!allowed.contains(target)) {
            return TransitionResult.denied(
                    "不允许从 " + current + " 切换到 " + target
                            + ", 合法目标: " + allowed);
        }
        return TransitionResult.ok();
    }

    /**
     * Decide whether converting a prediction should mutate the entity.
     *
     * @param current current prediction status
     * @param currentProjectId currently linked project id
     * @param requestedProjectId project id from command payload
     * @return conversion decision with resolved project id
     */
    public static ConversionResult validateConversion(
            final PredictionStatus current,
            final Long currentProjectId,
            final Long requestedProjectId) {
        Long resolvedProjectId = requestedProjectId != null ? requestedProjectId : currentProjectId;
        boolean alreadyConverted = current == PredictionStatus.CONVERTED;
        boolean projectIdUnchanged = Objects.equals(currentProjectId, resolvedProjectId);

        if (alreadyConverted && projectIdUnchanged) {
            return ConversionResult.noChange(resolvedProjectId);
        }

        TransitionResult transition = validateTransition(current, PredictionStatus.CONVERTED);
        if (!transition.allowed()) {
            return ConversionResult.denied(resolvedProjectId, transition.reason());
        }
        return ConversionResult.shouldSave(resolvedProjectId);
    }

    /**
     * Result of a transition validation.
     *
     * @param allowed whether transition is legal
     * @param reason  human-readable explanation if denied
     */
    public record TransitionResult(boolean allowed, String reason) {

        /** Create an accepted result.
         *
         * @return accepted transition result
         */
        public static TransitionResult ok() {
            return new TransitionResult(true, "");
        }

        /** Create a denied result with reason.
         *
         * @param reason the denial reason
         * @return denied transition result
         */
        public static TransitionResult denied(final String reason) {
            return new TransitionResult(false, reason);
        }
    }

    /**
     * Result of a conversion command validation.
     *
     * @param allowed whether conversion is legal
     * @param shouldSave whether shell should persist a changed entity
     * @param resolvedProjectId project id to write back, if present
     * @param reason human-readable explanation if denied
     */
    public record ConversionResult(
            boolean allowed,
            boolean shouldSave,
            Long resolvedProjectId,
            String reason) {

        /** Create an idempotent conversion result.
         *
         * @param resolvedProjectId retained project id
         * @return conversion result that does not need persistence
         */
        public static ConversionResult noChange(final Long resolvedProjectId) {
            return new ConversionResult(true, false, resolvedProjectId, "");
        }

        /** Create an accepted conversion result that should be persisted.
         *
         * @param resolvedProjectId project id to write back, if present
         * @return conversion result that requires persistence
         */
        public static ConversionResult shouldSave(final Long resolvedProjectId) {
            return new ConversionResult(true, true, resolvedProjectId, "");
        }

        /** Create a denied conversion result.
         *
         * @param resolvedProjectId resolved project id, if any
         * @param reason denial reason
         * @return denied conversion result
         */
        public static ConversionResult denied(
                final Long resolvedProjectId,
                final String reason) {
            return new ConversionResult(false, false, resolvedProjectId, reason);
        }
    }

    /**
     * Prediction status values.
     * Core policy uses its own enum to avoid coupling to JPA entity.
     */
    public enum PredictionStatus {
        /** Under observation. */
        WATCH,
        /** Recommended for action. */
        RECOMMEND,
        /** Converted to opportunity. */
        CONVERTED,
        /** Cancelled observation. */
        CANCELLED
    }
}
