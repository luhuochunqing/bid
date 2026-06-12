package com.xiyu.bid.warehouse.domain;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class WarehouseLedgerExportPolicy {
    public static final int COL_INDEX = 0, COL_NAME = 1, COL_TYPE = 2, COL_REGION = 3, COL_PROVINCE = 4;
    public static final int COL_ADDRESS = 5, COL_AREA = 6, COL_CONTACT = 7, COL_START = 8, COL_END = 9;
    public static final int COL_LESSOR = 10, COL_LESSEE = 11, COL_INVOICE_PERIOD = 12;
    public static final int COL_HAS_CERT = 13, COL_HAS_INVOICE = 14, COL_HAS_PHOTOS = 15;
    public static final int COL_CLOSE_PLAN = 16, COL_STATUS = 17, COL_REMARKS = 18;
    public static final int HEADER_COUNT = 19;
    private static final Set<Integer> BASIC_INFO = Set.of(COL_NAME, COL_TYPE, COL_REGION, COL_PROVINCE, COL_ADDRESS, COL_AREA, COL_CONTACT, COL_REMARKS);
    private static final Set<Integer> LEASE_INFO = Set.of(COL_START, COL_END, COL_LESSOR, COL_LESSEE, COL_INVOICE_PERIOD, COL_CLOSE_PLAN);
    private static final Set<Integer> DOC_STATUS = Set.of(COL_HAS_CERT, COL_HAS_INVOICE, COL_HAS_PHOTOS, COL_STATUS);
    public static final String[] HEADERS = {"序号", "仓库名称", "仓库类型", "所属区域", "所在省份", "具体地址", "面积", "区域联系人", "开始时间", "结束时间", "出租方", "承租方", "发票租期", "是否有产权证", "是否有发票", "是否有内外照片", "关仓计划", "状态", "备注"};
    public enum Section { BASIC, LEASE, DOC }
    private WarehouseLedgerExportPolicy() {}

    public static List<String[]> buildRows(List<? extends WarehouseReadModel> entities, Set<Section> enabled) {
        Set<Integer> cols = collectColumns(enabled);
        List<String[]> rows = new ArrayList<>(entities.size());
        int seq = 1;
        for (WarehouseReadModel e : entities) {
            String[] all = fullRow(e, seq++);
            String[] filtered = new String[HEADER_COUNT];
            for (int i = 0; i < HEADER_COUNT; i++) filtered[i] = cols.contains(i) ? all[i] : "";
            rows.add(filtered);
        }
        return rows;
    }
    public static String[] getHeaders(Set<Section> enabled) {
        Set<Integer> cols = collectColumns(enabled);
        String[] out = new String[HEADER_COUNT];
        for (int i = 0; i < HEADER_COUNT; i++) out[i] = cols.contains(i) ? HEADERS[i] : "";
        return out;
    }
    public static String[] getFullHeaders() { return HEADERS.clone(); }
    private static Set<Integer> collectColumns(Set<Section> enabled) {
        Set<Integer> cols = new java.util.LinkedHashSet<>();
        cols.add(COL_INDEX);
        if (enabled.contains(Section.BASIC)) cols.addAll(BASIC_INFO);
        if (enabled.contains(Section.LEASE)) cols.addAll(LEASE_INFO);
        if (enabled.contains(Section.DOC)) cols.addAll(DOC_STATUS);
        return cols;
    }
    private static String[] fullRow(WarehouseReadModel e, int seq) {
        return new String[]{String.valueOf(seq), nvl(e.getName()), displayName(e.getType()), nvl(e.getRegion()), nvl(e.getProvince()), nvl(e.getAddress()), e.getArea() != null ? e.getArea().toPlainString() : "", nvl(e.getContactPerson()), e.getStartDate() != null ? e.getStartDate().toString() : "", e.getEndDate() != null ? e.getEndDate().toString() : "", nvl(e.getLessor()), nvl(e.getLessee()), formatInvoicePeriod(e), boolLabel(e.getHasPropertyCert()), boolLabel(e.getHasInvoice()), boolLabel(e.getHasPhotos()), nvl(e.getClosePlan()), e.getStatus() != null ? e.getStatus().getDisplayName() : "", nvl(e.getRemarks())};
    }
    private static String formatInvoicePeriod(WarehouseReadModel e) {
        LocalDate s = e.getInvoicePeriodStart(), t = e.getInvoicePeriodEnd();
        if (s == null && t == null) return nvl(e.getInvoicePeriod());
        if (s != null && t != null) return s + " ~ " + t;
        return s != null ? s.toString() : t.toString();
    }
    private static String nvl(String s) { return s != null ? s : ""; }
    private static String boolLabel(Boolean b) { return Boolean.TRUE.equals(b) ? "是" : "否"; }
    private static String displayName(Enum<?> e) {
        if (e instanceof WarehouseType t) return t.getDisplayName();
        if (e instanceof WarehouseStatus s) return s.getDisplayName();
        return e != null ? e.name() : "";
    }
    public static long daysToExpiry(WarehouseReadModel e) {
        if (e.getEndDate() == null) return 0;
        return ChronoUnit.DAYS.between(LocalDate.now(), e.getEndDate());
    }
}
