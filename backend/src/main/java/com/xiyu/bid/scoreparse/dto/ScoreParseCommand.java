package com.xiyu.bid.scoreparse.dto;

/** 解析触发：source 缺省 MANUAL。 */
public record ScoreParseCommand(String source) {

    public String normalizedSource() {
        return "AUTO".equalsIgnoreCase(source) ? "AUTO" : "MANUAL";
    }
}
