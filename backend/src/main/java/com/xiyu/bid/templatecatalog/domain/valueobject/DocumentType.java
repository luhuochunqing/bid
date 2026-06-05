package com.xiyu.bid.templatecatalog.domain.valueobject;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum DocumentType {
    TECHNICAL_PROPOSAL("技术方案"),
    COMMERCIAL_RESPONSE("商务应答"),
    INDUSTRY_SOLUTION("行业方案"),
    IMPLEMENTATION_PLAN("实施方案"),
    QUALIFICATION_DOCUMENT("资格文件"),
    OTHER("其他");

    private final String label;

    DocumentType(String pLabel) {
        this.label = pLabel;
    }

    @JsonValue
    public String getLabel() {
        return label;
    }

    @JsonCreator
    public static DocumentType fromValue(String value) {
        return EnumValueParser.parseOrNull(DocumentType.class, value);
    }

    public static EnumParseResult<DocumentType> parse(String value) {
        return EnumValueParser.parse(DocumentType.class, value, "文档类型");
    }
}
