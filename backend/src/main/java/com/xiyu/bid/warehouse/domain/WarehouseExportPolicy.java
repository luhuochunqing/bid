package com.xiyu.bid.warehouse.domain;

import com.xiyu.bid.warehouse.infrastructure.WarehouseEntity;

import java.util.List;

/**
 * 纯核心：将 WarehouseEntity 列表转换为导出行数据结构。
 * 不含 I/O、不含副作用、不修改入参。
 */
public class WarehouseExportPolicy {

    public static final String[] HEADERS = {
            "仓库名称", "仓库类型", "所属区域", "所在省份", "具体地址",
            "面积(㎡)", "区域联系人", "租约开始时间", "租约结束时间",
            "出租方", "承租方", "发票租期", "关仓计划", "关仓原因",
            "产权证", "发票", "内外照片", "产权证备注", "状态"
    };

    private WarehouseExportPolicy() {
    }

    /**
     * 将实体列表转换为导出行数据（纯函数）。
     */
    public static List<String[]> buildRows(List<WarehouseEntity> entities) {
        return entities.stream()
                .map(WarehouseExportPolicy::toRow)
                .toList();
    }

    private static String[] toRow(WarehouseEntity e) {
        return new String[]{
                nvl(e.getName()),
                displayName(e.getType()),
                nvl(e.getRegion()),
                nvl(e.getProvince()),
                nvl(e.getAddress()),
                e.getArea() != null ? e.getArea().toPlainString() : "",
                nvl(e.getContactPerson()),
                e.getStartDate() != null ? e.getStartDate().toString() : "",
                e.getEndDate() != null ? e.getEndDate().toString() : "",
                nvl(e.getLessor()),
                nvl(e.getLessee()),
                nvl(e.getInvoicePeriod()),
                nvl(e.getClosePlan()),
                nvl(e.getCloseReason()),
                boolLabel(e.getHasPropertyCert()),
                boolLabel(e.getHasInvoice()),
                boolLabel(e.getHasPhotos()),
                nvl(e.getCertRemarks()),
                displayName(e.getStatus())
        };
    }

    private static String nvl(String s) {
        return s != null ? s : "";
    }

    private static String boolLabel(Boolean b) {
        return Boolean.TRUE.equals(b) ? "有" : "无";
    }

    private static String displayName(Enum<?> e) {
        if (e instanceof WarehouseType t) return t.getDisplayName();
        if (e instanceof WarehouseStatus s) return s.getDisplayName();
        return e != null ? e.name() : "";
    }
}
