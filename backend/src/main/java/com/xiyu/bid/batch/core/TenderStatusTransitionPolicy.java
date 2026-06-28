package com.xiyu.bid.batch.core;

import com.xiyu.bid.entity.Tender;

import java.util.EnumSet;
import java.util.Set;

/**
 * 投标状态迁移规则 (Pure Core — no Spring dependencies)
 */
public final class TenderStatusTransitionPolicy {

    public TenderStatusTransitionPolicy() {}

    public static boolean canTransition(Tender.Status currentStatus, Tender.Status targetStatus) {
        if (currentStatus == null || targetStatus == null) {
            return false;
        }
        if (currentStatus == targetStatus) {
            return true;
        }
        return allowedTargets(currentStatus).contains(targetStatus);
    }

    public static void assertTransition(Tender.Status currentStatus, Tender.Status targetStatus) {
        if (!canTransition(currentStatus, targetStatus)) {
            throw new IllegalArgumentException(
                    String.format("Tender status cannot transition from %s to %s", currentStatus, targetStatus)
            );
        }
    }

    /** 纯核心推荐：返回验证错误而非抛出异常 */
    public static java.util.Optional<String> validateTransition(Tender.Status currentStatus, Tender.Status targetStatus) {
        if (!canTransition(currentStatus, targetStatus)) {
            return java.util.Optional.of(
                    String.format("Tender status cannot transition from %s to %s", currentStatus, targetStatus));
        }
        return java.util.Optional.empty();
    }

    /**
     * 判断是否为终态（WON/LOST/ABANDONED）。终态不允许流转到其他状态。
     * 与 {@link #allowedTargets} 返回空集的语义一致，作为单一真相源避免重复列举。
     */
    public static boolean isTerminal(Tender.Status status) {
        if (status == null) {
            return false;
        }
        return allowedTargets(status).isEmpty();
    }

    private static Set<Tender.Status> allowedTargets(Tender.Status currentStatus) {
        return switch (currentStatus) {
            case PENDING_ASSIGNMENT -> EnumSet.of(Tender.Status.TRACKING, Tender.Status.ABANDONED);
            case TRACKING -> EnumSet.of(Tender.Status.PENDING_ASSIGNMENT, Tender.Status.EVALUATED, Tender.Status.ABANDONED);
            case EVALUATED -> EnumSet.of(Tender.Status.BIDDING, Tender.Status.ABANDONED);
            case BIDDING -> EnumSet.of(Tender.Status.WON, Tender.Status.LOST, Tender.Status.ABANDONED);
            case WON, LOST, ABANDONED -> EnumSet.noneOf(Tender.Status.class);
        };
    }
}
