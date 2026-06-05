package com.xiyu.bid.templatecatalog.domain.valueobject;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum IndustryType {
    GOVERNMENT("政府"),
    ENERGY("能源"),
    TRANSPORTATION("交通"),
    MEDICAL("医疗"),
    EDUCATION("教育"),
    MANUFACTURING("制造业"),
    INTERNET("互联网"),
    OTHER("其他");

    private final String label;

    IndustryType(String pLabel) {
        this.label = pLabel;
    }

    @JsonValue
    public String getLabel() {
        return label;
    }

    @JsonCreator
    public static IndustryType fromValue(String value) {
        return EnumValueParser.parseOrNull(IndustryType.class, value);
    }

    public static EnumParseResult<IndustryType> parse(String value) {
        return EnumValueParser.parse(IndustryType.class, value, "行业");
    }
}
