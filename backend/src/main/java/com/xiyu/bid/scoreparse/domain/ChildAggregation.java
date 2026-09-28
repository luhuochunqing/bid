package com.xiyu.bid.scoreparse.domain;

/** Scoring relationship between a fact and its direct child facts. */
public enum ChildAggregation {
    SUM,
    SHARED_CAP,
    BEST_OF,
    MUTUALLY_EXCLUSIVE,
    UNKNOWN;

    public static ChildAggregation legacyDefault(String value) {
        if (value == null || value.isBlank()) return SUM;
        try {
            return valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return UNKNOWN;
        }
    }

    public static ChildAggregation modelValue(String value) {
        if (value == null || value.isBlank()) return UNKNOWN;
        try {
            return valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return UNKNOWN;
        }
    }

    public boolean supportedByOriginalText(String evidence) {
        String source = evidence == null ? "" : evidence;
        boolean additive = source.matches("(?s).*(分别计分|分别计算|分值累加|累计计分).*");
        boolean sharedCap = !additive && source.matches("(?s).*(共享|共用|条件之一|任一条件).*");
        boolean bestOf = source.matches("(?s).*(择优|取最高|最高者|最高分者|按最高分).*");
        boolean exclusive = source.matches("(?s).*(互斥|任选其一|择一|只能选择其中一|仅选择其中一|不得同时|分档|档次|档位|优良中差|优\\s*[：:为]|良\\s*[：:为]|差\\s*[：:为]).*");
        return switch (this) {
            case SUM -> !sharedCap && !bestOf && !exclusive;
            case SHARED_CAP -> sharedCap;
            case BEST_OF -> bestOf;
            case MUTUALLY_EXCLUSIVE -> exclusive;
            case UNKNOWN -> false;
        };
    }
}
