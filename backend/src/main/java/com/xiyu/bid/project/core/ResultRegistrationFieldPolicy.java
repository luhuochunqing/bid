// Input: BidResultType + ResultInput (字段集合 + 证据附件 ids)
// Output: Decision (Allow | Deny{missing}) -- PRD §3.4.2 必填字段矩阵
// Pos: project/core/ - pure rule, no Spring/JPA
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.project.core;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * PRD §3.4 结果确认必填字段策略。
 *
 * <p>必填映射：
 * <ul>
 *   <li>WON: awardAmount(>0), evidenceFileIds(中标通知书)；contractStart/End 可选但若同时存在则 end >= start</li>
 *   <li>LOST: evidenceFileIds(中标公告)</li>
 *   <li>FAILED: evidenceFileIds(流标公告/说明), summary(流标原因)</li>
 *   <li>ABANDONED: evidenceFileIds(弃标说明), summary(弃标决策依据)</li>
 * </ul>
 *
 * <p>对 WON/LOST 子集，可由 shell 层桥接到
 * {@link com.xiyu.bid.bidresult.core.AwardRegistrationValidation} 做更宽口径校验，本策略仅覆盖
 * project lifecycle §3.4 的最小必填集，避免重复实现。
 */
public final class ResultRegistrationFieldPolicy {

    private ResultRegistrationFieldPolicy() {
    }

    public static Decision validate(ResultInput input) {
        if (input == null) {
            return new Decision.Deny(List.of("input"));
        }
        List<String> missing = new ArrayList<>();
        BidResultType rt = input.resultType();
        if (rt == null) {
            missing.add("resultType");
            return new Decision.Deny(Collections.unmodifiableList(missing));
        }
        if (!hasEvidence(input.evidenceFileIds())) {
            missing.add("evidenceFileIds");
        }
        switch (rt) {
            case WON -> {
                if (!isPositive(input.awardAmount())) {
                    missing.add("awardAmount");
                }
                if (!datesValid(input.contractStartDate(), input.contractEndDate())) {
                    missing.add("contractEndDate");
                }
            }
            case LOST -> {
                // 仅证据必填（已在前面统一加入）
            }
            case FAILED, ABANDONED -> {
                if (isBlank(input.summary())) {
                    missing.add("summary");
                }
            }
        }
        return missing.isEmpty()
                ? Decision.ALLOW
                : new Decision.Deny(Collections.unmodifiableList(missing));
    }

    private static boolean hasEvidence(List<Long> ids) {
        return ids != null && !ids.isEmpty();
    }

    private static boolean isPositive(BigDecimal v) {
        return v != null && v.compareTo(BigDecimal.ZERO) > 0;
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static boolean datesValid(LocalDate start, LocalDate end) {
        if (start == null || end == null) {
            return true; // 合同日期为可选；同时缺失视为合法
        }
        return !end.isBefore(start);
    }

    /** 结果登记输入。所有字段可为 null，由策略按 resultType 决定必填。 */
    public record ResultInput(
            BidResultType resultType,
            BigDecimal awardAmount,
            LocalDate contractStartDate,
            LocalDate contractEndDate,
            List<Long> evidenceFileIds,
            String summary) {

        public static Builder builder() {
            return new Builder();
        }

        public static final class Builder {
            private BidResultType resultType;
            private BigDecimal awardAmount;
            private LocalDate contractStartDate;
            private LocalDate contractEndDate;
            private List<Long> evidenceFileIds;
            private String summary;

            public Builder resultType(BidResultType v) { this.resultType = v; return this; }
            public Builder awardAmount(BigDecimal v) { this.awardAmount = v; return this; }
            public Builder contractStartDate(LocalDate v) { this.contractStartDate = v; return this; }
            public Builder contractEndDate(LocalDate v) { this.contractEndDate = v; return this; }
            public Builder evidenceFileIds(List<Long> v) { this.evidenceFileIds = v; return this; }
            public Builder summary(String v) { this.summary = v; return this; }

            public ResultInput build() {
                return new ResultInput(resultType, awardAmount, contractStartDate,
                        contractEndDate, evidenceFileIds, summary);
            }
        }
    }

    /** Sealed Decision: Allow | Deny{missing}. */
    public sealed interface Decision permits Decision.Allow, Decision.Deny {
        Decision ALLOW = new Allow();

        default boolean allowed() {
            return this instanceof Allow;
        }

        record Allow() implements Decision {
        }

        record Deny(List<String> missing) implements Decision {
            public String reason() {
                return "缺少必填字段：" + String.join(",", missing);
            }
        }
    }
}
