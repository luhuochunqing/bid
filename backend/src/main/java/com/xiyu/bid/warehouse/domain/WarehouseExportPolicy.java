package com.xiyu.bid.warehouse.domain;

import com.xiyu.bid.warehouse.infrastructure.WarehouseAttachmentEntity;
import com.xiyu.bid.warehouse.infrastructure.WarehouseEntity;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 纯核心：将 WarehouseEntity 列表 + 附件映射转换为导出行数据结构。
 * 29 列 = 24 基础字段（与导入模板对齐）+ 4 系统字段 + 1 附件文件名清单。
 * 不含 I/O、不含副作用、不修改入参。
 */
public class WarehouseExportPolicy {

    public static final String[] HEADERS = {
            // 24 基础字段（与 WarehouseImportPolicy.TEMPLATE_HEADERS 对齐）
            "仓库名称", "仓库类型", "省份", "地址", "面积", "区域", "联系人", "备注",
            "开始时间", "结束时间", "开始时间(精简版)", "到期天数",
            "出租方", "承租方", "发票租期起", "发票租期止", "关仓计划",
            "是否有产权证", "产权证附件",
            "是否有发票", "发票附件",
            "是否有仓库照片", "照片附件",
            "资料备注",
            // 5 系统字段
            "创建时间", "更新时间", "创建人", "更新人", "附件文件名清单"
    };

    public static final int HEADER_COUNT = HEADERS.length;

    private WarehouseExportPolicy() {
    }

    /**
     * 将实体列表 + 附件映射转换为导出行数据（纯函数）。
     */
    public static List<String[]> buildRows(List<WarehouseEntity> entities,
                                           Map<Long, List<WarehouseAttachmentEntity>> attachmentsByWhId) {
        return entities.stream()
                .map(e -> toRow(e, attachmentsByWhId.getOrDefault(e.getId(), List.of())))
                .toList();
    }

    private static String[] toRow(WarehouseEntity e, List<WarehouseAttachmentEntity> attachments) {
        boolean hasPropertyCert = Boolean.TRUE.equals(e.getHasPropertyCert());
        boolean hasInvoice = Boolean.TRUE.equals(e.getHasInvoice());
        boolean hasPhotos = Boolean.TRUE.equals(e.getHasPhotos());

        String startDate = e.getStartDate() != null ? e.getStartDate().toString() : "";
        String endDate = e.getEndDate() != null ? e.getEndDate().toString() : "";
        String simpleStartDate = startDate.length() >= 7 ? startDate.substring(0, 7) : startDate;
        String daysToExpiry = computeDaysToExpiry(e);

        String invoiceStart = e.getInvoicePeriodStart() != null ? e.getInvoicePeriodStart().toString() : "";
        String invoiceEnd = e.getInvoicePeriodEnd() != null ? e.getInvoicePeriodEnd().toString() : "";

        // 找各类型附件文件名
        String propertyCertFile = findAttachName(attachments, WarehouseAttachmentType.PROPERTY_CERTIFICATE);
        String invoiceFile = findAttachName(attachments, WarehouseAttachmentType.INVOICE);
        String photosFile = joinAttachNames(attachments, WarehouseAttachmentType.PHOTOS);

        String attachmentList = attachments.stream()
                .map(WarehouseAttachmentEntity::getOriginalFilename)
                .collect(Collectors.joining("; "));

        return new String[]{
                // 24 基础字段
                nvl(e.getName()),
                displayName(e.getType()),
                nvl(e.getProvince()),
                nvl(e.getAddress()),
                e.getArea() != null ? e.getArea().toPlainString() : "",
                nvl(e.getRegion()),
                nvl(e.getContactPerson()),
                nvl(e.getRemarks()),
                startDate, endDate, simpleStartDate, daysToExpiry,
                nvl(e.getLessor()), nvl(e.getLessee()),
                invoiceStart, invoiceEnd,
                nvl(e.getClosePlan()),
                boolLabel(hasPropertyCert), propertyCertFile,
                boolLabel(hasInvoice), invoiceFile,
                boolLabel(hasPhotos), photosFile,
                nvl(e.getCertRemarks()),
                // 5 系统字段
                e.getCreatedAt() != null ? e.getCreatedAt().toString() : "",
                e.getUpdatedAt() != null ? e.getUpdatedAt().toString() : "",
                e.getCreatedBy() != null ? e.getCreatedBy().toString() : "",
                e.getUpdatedBy() != null ? e.getUpdatedBy().toString() : "",
                attachmentList
        };
    }

    private static String computeDaysToExpiry(WarehouseEntity e) {
        if (e.getEndDate() == null) return "";
        long days = java.time.temporal.ChronoUnit.DAYS.between(
                java.time.LocalDate.now(), e.getEndDate());
        return String.valueOf(days);
    }

    private static String findAttachName(List<WarehouseAttachmentEntity> attachments, WarehouseAttachmentType type) {
        return attachments.stream()
                .filter(a -> a.getType() == type)
                .map(WarehouseAttachmentEntity::getOriginalFilename)
                .findFirst()
                .orElse("");
    }

    private static String joinAttachNames(List<WarehouseAttachmentEntity> attachments, WarehouseAttachmentType type) {
        return attachments.stream()
                .filter(a -> a.getType() == type)
                .map(WarehouseAttachmentEntity::getOriginalFilename)
                .collect(Collectors.joining("; "));
    }

    private static String nvl(String s) {
        return s != null ? s : "";
    }

    private static String boolLabel(Boolean b) {
        return Boolean.TRUE.equals(b) ? "是" : "否";
    }

    private static String displayName(Enum<?> e) {
        if (e instanceof WarehouseType t) return t.getDisplayName();
        if (e instanceof WarehouseStatus s) return s.getDisplayName();
        return e != null ? e.name() : "";
    }
}
