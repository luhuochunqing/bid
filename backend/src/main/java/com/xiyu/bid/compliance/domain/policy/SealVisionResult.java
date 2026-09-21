package com.xiyu.bid.compliance.domain.policy;

import java.util.List;

/** 逐页印章视觉识别结果。图片不落库，结果只在本次质检内参与融合。 */
public record SealVisionResult(
        boolean available,
        String unavailableReason,
        List<Integer> targetPages,
        List<Observation> observations,
        String model
) {

    public SealVisionResult {
        unavailableReason = unavailableReason == null ? "" : unavailableReason;
        targetPages = targetPages == null ? List.of() : List.copyOf(targetPages);
        observations = observations == null ? List.of() : List.copyOf(observations);
        model = model == null ? "" : model;
    }

    public enum SealType {
        LEGAL_REP_SEAL,
        COMPANY_SEAL,
        CROSS_PAGE_SEAL,
        UNKNOWN
    }

    public record Observation(
            int pageNumber,
            SealType type,
            boolean detected,
            double confidence,
            Double x,
            Double y,
            Double width,
            Double height
    ) {
    }

    public static SealVisionResult available(
            final List<Integer> targetPages,
            final List<Observation> observations,
            final String model) {
        return new SealVisionResult(true, "", targetPages, observations, model);
    }

    public static SealVisionResult unavailable(final String reason) {
        return new SealVisionResult(false, reason, List.of(), List.of(), "");
    }
}
