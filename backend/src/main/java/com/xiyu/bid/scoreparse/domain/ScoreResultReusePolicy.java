package com.xiyu.bid.scoreparse.domain;

import java.util.List;
import java.util.Objects;

/** Source identity belongs to each item; the owner's execution status is independent. */
public final class ScoreResultReusePolicy {
    public record Identity(String fileHash, String ruleFingerprint, String engineVersion,String modelPolicyFingerprint) {
        public boolean known() { return present(fileHash) && present(ruleFingerprint) && present(engineVersion) && present(modelPolicyFingerprint); }
        private static boolean present(String value) { return value != null && !value.isBlank(); }
    }
    public record Source(Identity identity, String coverageStatus, String outcome, Long originTaskId) {}

    public boolean preserveEligible(Identity current, Source source) {
        return current != null && current.known() && source != null && source.originTaskId() != null
                && Objects.equals(current, source.identity())
                && List.of("SCORED", "SUBJECTIVE").contains(Objects.toString(source.outcome(), ""));
    }
    public boolean autoSkipEligible(Identity current, List<Source> targets, int targetCount) {
        return targetCount > 0 && targets.size() == targetCount && targets.stream()
                .allMatch(source -> preserveEligible(current, source) && "COMPLETE".equals(source.coverageStatus()));
    }
}
