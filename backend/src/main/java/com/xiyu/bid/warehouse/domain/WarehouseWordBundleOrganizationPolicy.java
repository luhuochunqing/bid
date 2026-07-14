package com.xiyu.bid.warehouse.domain;

import java.text.Collator;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * 仓库 Word 合订本组织规则：纯核心，无 Spring/IO 依赖。
 *
 * 负责 CO-582 §3.4 / §3.5 / §3.6 业务规则：
 * - 附件类型 → Word 三级标题文本映射（产权证→不动产权证明、内外照片→仓库内外照片）
 * - 租赁合同三级标题需带租期日期（yyyy.MM.dd-yyyy.MM.dd）
 * - 按省份拼音字典序升序、仓库名字典序升序排序
 * - 照片附件按原文件名升序排序
 * - 附件分类固定顺序常量
 *
 * 受 FPJavaArchitectureTest 保护：禁止依赖 Controller/Repository/Config/Adapter/Gateway。
 */
public final class WarehouseWordBundleOrganizationPolicy {

    /**
     * 附件分类固定顺序（CO-582 §3.5）：
     * 租赁合同 → 不动产权证明 → 发票 → 仓库内外照片。
     */
    public static final List<WarehouseAttachmentType> ATTACHMENT_TYPE_ORDER = List.of(
            WarehouseAttachmentType.LEASE_CONTRACT,
            WarehouseAttachmentType.PROPERTY_CERTIFICATE,
            WarehouseAttachmentType.INVOICE,
            WarehouseAttachmentType.PHOTOS
    );

    /**
     * 中文拼音字典序排序器（CO-582 §3.5 要求按省份/仓库名拼音字典序升序）。
     * 注意：String.compareTo 是 Unicode 序，不满足拼音字典序要求。
     */
    private static final Collator CHINESE_COLLATOR = Collator.getInstance(Locale.CHINESE);

    /**
     * 租期日期格式：yyyy.MM.dd（CO-582 §3.4 示例 2021.01.15-2029.01.14）。
     */
    private static final DateTimeFormatter LEASE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy.MM.dd");

    private WarehouseWordBundleOrganizationPolicy() {
        // 纯核心工具类，禁止实例化
    }

    /**
     * 附件类型 → Word 三级标题文本（不带租期）。
     * <p>
     * CO-582 §3.6 明确要求：
     * - 产权证 → "不动产权证明"（非 displayName 的"产权证"）
     * - 内外照片 → "仓库内外照片"（非 displayName 的"内外照片"）
     */
    public static String wordSectionTitle(WarehouseAttachmentType type) {
        Objects.requireNonNull(type, "type");
        return switch (type) {
            case LEASE_CONTRACT -> "租赁合同";
            case PROPERTY_CERTIFICATE -> "不动产权证明";
            case INVOICE -> "发票";
            case PHOTOS -> "仓库内外照片";
        };
    }

    /**
     * 附件类型 → Word 三级标题文本（带租期，仅租赁合同生效）。
     * <p>
     * CO-582 §3.4 示例：租赁合同（2021.01.15-2029.01.14）。
     * 非租赁合同类型忽略 warehouse 参数，等价于 {@link #wordSectionTitle(WarehouseAttachmentType)}。
     *
     * @param type      附件类型
     * @param warehouse 仓库信息（租赁合同类型时用于取 startDate/endDate），不能为 null
     * @throws NullPointerException 当 type 为 LEASE_CONTRACT 且 warehouse 为 null 时
     */
    public static String wordSectionTitle(WarehouseAttachmentType type, WarehouseReadModel warehouse) {
        Objects.requireNonNull(type, "type");
        if (type != WarehouseAttachmentType.LEASE_CONTRACT) {
            return wordSectionTitle(type);
        }
        Objects.requireNonNull(warehouse, "warehouse");
        LocalDate start = warehouse.getStartDate();
        LocalDate end = warehouse.getEndDate();
        return "租赁合同（" + start.format(LEASE_DATE_FORMAT) + "-" + end.format(LEASE_DATE_FORMAT) + "）";
    }

    /**
     * 按省份拼音字典序升序，同省份按仓库名拼音字典序升序排序。
     * <p>
     * CO-582 §3.5：一级标题（省份）、二级标题（仓库名）均按拼音字典序升序。
     *
     * @param warehouses 仓库列表，不能为 null
     * @return 新列表（不修改输入），按省份+仓库名拼音字典序升序排序
     */
    public static <W extends WarehouseReadModel> List<W> sortByProvinceThenName(List<W> warehouses) {
        Objects.requireNonNull(warehouses, "warehouses");
        List<W> copy = new ArrayList<>(warehouses);
        copy.sort(Comparator
                .comparing(WarehouseReadModel::getProvince, CHINESE_COLLATOR)
                .thenComparing(WarehouseReadModel::getName, CHINESE_COLLATOR));
        return copy;
    }

    /**
     * 按附件原文件名拼音字典序升序排序。
     * <p>
     * CO-582 §3.5：四级小标题（仅照片）按原文件名升序。
     *
     * @param attachments 附件列表，不能为 null
     * @return 新列表（不修改输入），按原文件名升序排序
     */
    public static <A extends WarehouseAttachmentReadModel> List<A> sortAttachmentsByFilename(List<A> attachments) {
        Objects.requireNonNull(attachments, "attachments");
        List<A> copy = new ArrayList<>(attachments);
        copy.sort(Comparator.comparing(WarehouseAttachmentReadModel::getOriginalFilename, CHINESE_COLLATOR));
        return copy;
    }
}
