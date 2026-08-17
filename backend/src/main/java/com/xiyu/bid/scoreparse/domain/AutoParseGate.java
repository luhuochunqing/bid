package com.xiyu.bid.scoreparse.domain;

/** 自动新建解析：仅从未有过 PARSE 且无评分项时允许。 */
public final class AutoParseGate {

    private AutoParseGate() {
    }

    public static boolean allowAutoCreate(boolean hasParseHistory, boolean hasItems) {
        return !hasParseHistory && !hasItems;
    }
}
