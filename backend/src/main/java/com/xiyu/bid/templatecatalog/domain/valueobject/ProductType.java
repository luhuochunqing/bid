package com.xiyu.bid.templatecatalog.domain.valueobject;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum ProductType {
    SMART_CITY("智慧城市"),
    SMART_TRANSPORTATION("智慧交通"),
    SMART_PARK("智慧园区"),
    MES("MES"),
    ERP("ERP"),
    DATA_MIDDLE_PLATFORM("数据中台"),
    E_COMMERCE_PLATFORM("电商平台"),
    OTHER("其他");

    private final String label;

    ProductType(String pLabel) {
        this.label = pLabel;
    }

    @JsonValue
    public String getLabel() {
        return label;
    }

    @JsonCreator
    public static ProductType fromValue(String value) {
        return EnumValueParser.parseOrNull(ProductType.class, value);
    }

    public static EnumParseResult<ProductType> parse(String value) {
        return EnumValueParser.parse(ProductType.class, value, "产品类型");
    }
}
