package com.xiyu.bid.brandauth.manufacturer.domain.valueobject;

import java.util.Optional;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum ProductLine {
    TOOLS("工具"),
    TOOL_CONSUMABLES("工具耗材"),
    CUTTING_TOOLS("刀具"),
    MEASURING_TOOLS("量具"),
    WELDING("焊接"),
    MACHINE_TOOLS("机床"),
    ABRASIVES("磨具"),
    LUBRICATION("润滑"),
    ADHESIVES("胶粘"),
    WORKSHOP_CHEMICALS("车间化学品"),
    LABOR_PROTECTION("劳保"),
    SAFETY("安全"),
    FIRE_PROTECTION("消防"),
    HANDLING("搬运"),
    STORAGE("存储"),
    WORKSTATION("工位"),
    PACKAGING("包材"),
    CLEANING("清洁"),
    OFFICE("办公"),
    REFRIGERATION("制冷"),
    HVAC("暖通"),
    INDUSTRIAL_CONTROL("工控"),
    LOW_VOLTAGE("低压"),
    ELECTRICAL("电工"),
    LIGHTING("照明"),
    BEARINGS("轴承"),
    BELTS("皮带"),
    MACHINERY("机械"),
    PNEUMATIC("气动"),
    HYDRAULIC("液压"),
    PIPE_VALVES("管阀"),
    PUMPS("泵"),
    FASTENERS("紧固"),
    SEALS("密封"),
    INDUSTRIAL_TESTING("工业检测"),
    LAB_PRODUCTS("实验室产品"),
    CORPORATE_WELFARE("企业福礼"),
    EMERGENCY_RESCUE("紧急救护"),
    CONSTRUCTION_MATERIALS("建工材料");

    private final String displayName;

    ProductLine(String displayName) {
        this.displayName = displayName;
    }

    @JsonValue
    public String getDisplayName() {
        return displayName;
    }

    @JsonCreator
    public static ProductLine fromString(String value) {
        for (ProductLine pl : values()) {
            if (pl.name().equalsIgnoreCase(value)
                    || pl.displayName.equals(value)) {
                return pl;
            }
        }
        return null;
    }

    public static Optional<ProductLine> fromStringOptional(String value) {
        for (ProductLine pl : values()) {
            if (pl.name().equalsIgnoreCase(value)
                    || pl.displayName.equals(value)) {
                return Optional.of(pl);
            }
        }
        return Optional.empty();
    }
}
