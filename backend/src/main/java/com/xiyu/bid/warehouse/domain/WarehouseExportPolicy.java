package com.xiyu.bid.warehouse.domain;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class WarehouseExportPolicy {
    public static final String[] HEADERS = {
            "仓库名称", "仓库类型", "省份", "地址", "面积", "区域", "联系人", "备注",
            "开始时间", "结束时间",
            "出租方", "承租方", "发票租期起", "发票租期止", "关仓计划",
            "是否有产权证", "产权证附件", "是否有发票", "发票附件",
            "是否有仓库照片", "照片附件", "资料备注",
            "创建时间", "更新时间", "创建人", "更新人", "附件文件名清单"
    };
    public static final int HEADER_COUNT = HEADERS.length;
    private WarehouseExportPolicy() {}

    public static <A extends WarehouseAttachmentReadModel> List<String[]> buildRows(
            List<? extends WarehouseReadModel> entities,
            Map<Long, List<A>> attachmentsByWhId,
            Map<Long, String> usernameById) {
        return entities.stream()
                .map(e -> toRow(e, attachmentsByWhId.getOrDefault(e.getId(), List.of()), usernameById))
                .toList();
    }

    private static <A extends WarehouseAttachmentReadModel> String[] toRow(WarehouseReadModel e, List<A> attachments, Map<Long, String> usernameById) {
        boolean hasPropertyCert = Boolean.TRUE.equals(e.getHasPropertyCert());
        boolean hasInvoice = Boolean.TRUE.equals(e.getHasInvoice());
        boolean hasPhotos = Boolean.TRUE.equals(e.getHasPhotos());
        String startDate = e.getStartDate() != null ? e.getStartDate().toString() : "";
        String endDate = e.getEndDate() != null ? e.getEndDate().toString() : "";
        String invoiceStart = e.getInvoicePeriodStart() != null ? e.getInvoicePeriodStart().toString() : "";
        String invoiceEnd = e.getInvoicePeriodEnd() != null ? e.getInvoicePeriodEnd().toString() : "";
        String propertyCertFile = findAttachName(attachments, WarehouseAttachmentType.PROPERTY_CERTIFICATE);
        String invoiceFile = findAttachName(attachments, WarehouseAttachmentType.INVOICE);
        String photosFile = joinAttachNames(attachments, WarehouseAttachmentType.PHOTOS);
        String attachmentList = attachments.stream().map(WarehouseAttachmentReadModel::getOriginalFilename).collect(Collectors.joining("; "));
        String createdByName = e.getCreatedBy() != null ? usernameById.getOrDefault(e.getCreatedBy(), "") : "";
        String updatedByName = e.getUpdatedBy() != null ? usernameById.getOrDefault(e.getUpdatedBy(), "") : "";
        return new String[]{
                nvl(e.getName()), displayName(e.getType()), nvl(e.getProvince()), nvl(e.getAddress()),
                e.getArea() != null ? e.getArea().toPlainString() : "", nvl(e.getRegion()),
                nvl(e.getContactPerson()), nvl(e.getRemarks()),
                startDate, endDate,
                nvl(e.getLessor()), nvl(e.getLessee()), invoiceStart, invoiceEnd, nvl(e.getClosePlan()),
                boolLabel(hasPropertyCert), propertyCertFile, boolLabel(hasInvoice), invoiceFile,
                boolLabel(hasPhotos), photosFile, nvl(e.getCertRemarks()),
                e.getCreatedAt() != null ? e.getCreatedAt().toString() : "",
                e.getUpdatedAt() != null ? e.getUpdatedAt().toString() : "",
                createdByName, updatedByName, attachmentList
        };
    }
    private static <A extends WarehouseAttachmentReadModel> String findAttachName(List<A> attachments, WarehouseAttachmentType type) {
        return attachments.stream().filter(a -> a.getType() == type).map(WarehouseAttachmentReadModel::getOriginalFilename).findFirst().orElse("");
    }
    private static <A extends WarehouseAttachmentReadModel> String joinAttachNames(List<A> attachments, WarehouseAttachmentType type) {
        return attachments.stream().filter(a -> a.getType() == type).map(WarehouseAttachmentReadModel::getOriginalFilename).collect(Collectors.joining("; "));
    }
    private static String nvl(String s) { return s != null ? s : ""; }
    private static String boolLabel(Boolean b) { return Boolean.TRUE.equals(b) ? "是" : "否"; }
    private static String displayName(Enum<?> e) {
        if (e instanceof WarehouseType t) return t.getDisplayName();
        if (e instanceof WarehouseStatus s) return s.getDisplayName();
        return e != null ? e.name() : "";
    }
}
