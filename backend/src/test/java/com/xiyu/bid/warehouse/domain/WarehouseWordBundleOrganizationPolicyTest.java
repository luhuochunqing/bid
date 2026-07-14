package com.xiyu.bid.warehouse.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * WarehouseWordBundleOrganizationPolicy 单元测试。
 *
 * 覆盖 CO-582 §3.4 / §3.5 / §3.6 业务规则：
 * - 附件类型 → Word 三级标题文本映射（含产权证→不动产权证明、内外照片→仓库内外照片）
 * - 租赁合同三级标题需带租期日期（yyyy.MM.dd-yyyy.MM.dd）
 * - 按省份拼音字典序升序、仓库名字典序升序排序（Collator.getInstance(Locale.CHINESE)）
 * - 照片附件按原文件名升序排序
 * - 附件分类固定顺序常量
 */
class WarehouseWordBundleOrganizationPolicyTest {

    // ========== §3.6 wordSectionTitle 映射 ==========

    @Test
    void wordSectionTitle_leaseContractWithoutWarehouse_returnsPlainTitle() {
        assertThat(WarehouseWordBundleOrganizationPolicy.wordSectionTitle(WarehouseAttachmentType.LEASE_CONTRACT))
                .isEqualTo("租赁合同");
    }

    @Test
    void wordSectionTitle_propertyCertificate_returnsCorrectTitle() {
        // 需求 §3.6：产权证 → "不动产权证明"（非 displayName 的"产权证"）
        assertThat(WarehouseWordBundleOrganizationPolicy.wordSectionTitle(WarehouseAttachmentType.PROPERTY_CERTIFICATE))
                .isEqualTo("不动产权证明");
    }

    @Test
    void wordSectionTitle_invoice_returnsCorrectTitle() {
        assertThat(WarehouseWordBundleOrganizationPolicy.wordSectionTitle(WarehouseAttachmentType.INVOICE))
                .isEqualTo("发票");
    }

    @Test
    void wordSectionTitle_photos_returnsCorrectTitle() {
        // 需求 §3.6：内外照片 → "仓库内外照片"（非 displayName 的"内外照片"）
        assertThat(WarehouseWordBundleOrganizationPolicy.wordSectionTitle(WarehouseAttachmentType.PHOTOS))
                .isEqualTo("仓库内外照片");
    }

    // ========== §3.4 租赁合同带租期日期 ==========

    @Test
    void wordSectionTitle_leaseContractWithWarehouse_returnsTitleWithLeasePeriod() {
        WarehouseReadModel warehouse = stubWarehouse("测试仓库", "浙江省",
                LocalDate.of(2021, 1, 15), LocalDate.of(2029, 1, 14));

        String title = WarehouseWordBundleOrganizationPolicy.wordSectionTitle(
                WarehouseAttachmentType.LEASE_CONTRACT, warehouse);

        // 需求 §3.4 示例：租赁合同（2021.01.15-2029.01.14）
        assertThat(title).isEqualTo("租赁合同（2021.01.15-2029.01.14）");
    }

    @Test
    void wordSectionTitle_nonLeaseContractWithWarehouse_ignoresWarehouse() {
        WarehouseReadModel warehouse = stubWarehouse("测试仓库", "浙江省",
                LocalDate.of(2021, 1, 15), LocalDate.of(2029, 1, 14));

        // 非租赁合同类型，不应附带日期
        assertThat(WarehouseWordBundleOrganizationPolicy.wordSectionTitle(
                WarehouseAttachmentType.PROPERTY_CERTIFICATE, warehouse))
                .isEqualTo("不动产权证明");
        assertThat(WarehouseWordBundleOrganizationPolicy.wordSectionTitle(
                WarehouseAttachmentType.INVOICE, warehouse))
                .isEqualTo("发票");
        assertThat(WarehouseWordBundleOrganizationPolicy.wordSectionTitle(
                WarehouseAttachmentType.PHOTOS, warehouse))
                .isEqualTo("仓库内外照片");
    }

    @Test
    void wordSectionTitle_leaseContractWithNullWarehouse_throwsNpe() {
        // 契约约束：租赁合同 + warehouse 不能为 null
        assertThatThrownBy(() -> WarehouseWordBundleOrganizationPolicy.wordSectionTitle(
                WarehouseAttachmentType.LEASE_CONTRACT, null))
                .isInstanceOf(NullPointerException.class);
    }

    // ========== §3.5 ATTACHMENT_TYPE_ORDER 固定顺序 ==========

    @Test
    void attachmentTypeOrder_isFixedSequence() {
        List<WarehouseAttachmentType> order = WarehouseWordBundleOrganizationPolicy.ATTACHMENT_TYPE_ORDER;

        // 需求 §3.5：租赁合同 → 不动产权证明 → 发票 → 仓库内外照片
        assertThat(order).containsExactly(
                WarehouseAttachmentType.LEASE_CONTRACT,
                WarehouseAttachmentType.PROPERTY_CERTIFICATE,
                WarehouseAttachmentType.INVOICE,
                WarehouseAttachmentType.PHOTOS);
    }

    @Test
    void attachmentTypeOrder_isImmutable() {
        List<WarehouseAttachmentType> order = WarehouseWordBundleOrganizationPolicy.ATTACHMENT_TYPE_ORDER;

        assertThatThrownBy(() -> order.add(WarehouseAttachmentType.INVOICE))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    // ========== §3.5 sortByProvinceThenName 拼音字典序 ==========

    @Test
    void sortByProvinceThenName_sortsByProvincePinyinThenWarehouseNamePinyin() {
        // 拼音字典序：北京(b) < 上海(s) < 浙江(z)
        // Unicode 序：上海(s) < 北京(b) < 浙江(z)  —— 错误的排序
        WarehouseReadModel shanghaiWh = stubWarehouse("上海仓A", "上海", null, null);
        WarehouseReadModel beijingWh = stubWarehouse("北京仓B", "北京", null, null);
        WarehouseReadModel zhejiangWh = stubWarehouse("浙江仓C", "浙江", null, null);

        List<WarehouseReadModel> input = new ArrayList<>(List.of(shanghaiWh, zhejiangWh, beijingWh));
        List<WarehouseReadModel> sorted = WarehouseWordBundleOrganizationPolicy.sortByProvinceThenName(input);

        // 期望拼音字典序：北京 → 上海 → 浙江
        assertThat(sorted).containsExactly(beijingWh, shanghaiWh, zhejiangWh);
    }

    @Test
    void sortByProvinceThenName_sameProvinceSortsByWarehouseNamePinyin() {
        WarehouseReadModel whA = stubWarehouse("宁波仓", "浙江", null, null);
        WarehouseReadModel whB = stubWarehouse("杭州仓", "浙江", null, null);
        WarehouseReadModel whC = stubWarehouse("安吉仓", "浙江", null, null);

        List<WarehouseReadModel> input = new ArrayList<>(List.of(whA, whB, whC));
        List<WarehouseReadModel> sorted = WarehouseWordBundleOrganizationPolicy.sortByProvinceThenName(input);

        // 拼音字典序：安吉(anji) < 杭州(hangzhou) < 宁波(ningbo)
        assertThat(sorted).containsExactly(whC, whB, whA);
    }

    @Test
    void sortByProvinceThenName_emptyList_returnsEmptyList() {
        List<WarehouseReadModel> sorted = WarehouseWordBundleOrganizationPolicy.sortByProvinceThenName(new ArrayList<>());
        assertThat(sorted).isEmpty();
    }

    @Test
    void sortByProvinceThenName_doesNotMutateInput() {
        WarehouseReadModel whA = stubWarehouse("宁波仓", "浙江", null, null);
        WarehouseReadModel whB = stubWarehouse("杭州仓", "浙江", null, null);
        List<WarehouseReadModel> input = new ArrayList<>(List.of(whA, whB));

        WarehouseWordBundleOrganizationPolicy.sortByProvinceThenName(input);

        // 原列表顺序不应被修改
        assertThat(input).containsExactly(whA, whB);
    }

    @Test
    void sortByProvinceThenName_nullInput_throwsNpe() {
        assertThatThrownBy(() -> WarehouseWordBundleOrganizationPolicy.sortByProvinceThenName(null))
                .isInstanceOf(NullPointerException.class);
    }

    // ========== §3.5 sortAttachmentsByFilename 原文件名升序 ==========

    @Test
    void sortAttachmentsByFilename_sortsByOriginalFilenameAscending() {
        WarehouseAttachmentReadModel att1 = attachment("zebra.jpg");
        WarehouseAttachmentReadModel att2 = attachment("apple.jpg");
        WarehouseAttachmentReadModel att3 = attachment("mango.png");

        List<WarehouseAttachmentReadModel> input = new ArrayList<>(List.of(att1, att2, att3));
        List<WarehouseAttachmentReadModel> sorted = WarehouseWordBundleOrganizationPolicy.sortAttachmentsByFilename(input);

        // 原文件名升序：apple < mango < zebra
        assertThat(sorted).containsExactly(att2, att3, att1);
    }

    @Test
    void sortAttachmentsByFilename_chineseFilenames_sortsByChineseCollator() {
        WarehouseAttachmentReadModel att1 = attachment("仓库正面.jpg");  // 仓 cāng
        WarehouseAttachmentReadModel att2 = attachment("侧面照.jpg");      // 侧 cè
        WarehouseAttachmentReadModel att3 = attachment("背面.jpg");        // 背 bèi

        List<WarehouseAttachmentReadModel> input = new ArrayList<>(List.of(att1, att2, att3));
        List<WarehouseAttachmentReadModel> sorted = WarehouseWordBundleOrganizationPolicy.sortAttachmentsByFilename(input);

        // 拼音字典序：背(bèi) < 仓(cāng) < 侧(cè)
        assertThat(sorted).containsExactly(att3, att1, att2);
    }

    @Test
    void sortAttachmentsByFilename_emptyList_returnsEmptyList() {
        List<WarehouseAttachmentReadModel> sorted =
                WarehouseWordBundleOrganizationPolicy.sortAttachmentsByFilename(new ArrayList<>());
        assertThat(sorted).isEmpty();
    }

    @Test
    void sortAttachmentsByFilename_doesNotMutateInput() {
        WarehouseAttachmentReadModel att1 = attachment("zebra.jpg");
        WarehouseAttachmentReadModel att2 = attachment("apple.jpg");
        List<WarehouseAttachmentReadModel> input = new ArrayList<>(List.of(att1, att2));

        WarehouseWordBundleOrganizationPolicy.sortAttachmentsByFilename(input);

        assertThat(input).containsExactly(att1, att2);
    }

    // ========== 测试辅助方法 ==========

    private static WarehouseReadModel stubWarehouse(String name, String province,
                                                     LocalDate startDate, LocalDate endDate) {
        return new WarehouseReadModel() {
            @Override public Long getId() { return 1L; }
            @Override public String getName() { return name; }
            @Override public WarehouseType getType() { return WarehouseType.SELF_OPERATED; }
            @Override public String getRegion() { return "测试区域"; }
            @Override public String getProvince() { return province; }
            @Override public String getAddress() { return "测试地址"; }
            @Override public BigDecimal getArea() { return BigDecimal.ONE; }
            @Override public String getContactPerson() { return "联系人"; }
            @Override public String getRemarks() { return null; }
            @Override public LocalDate getStartDate() { return startDate; }
            @Override public LocalDate getEndDate() { return endDate; }
            @Override public String getLessor() { return "出租方"; }
            @Override public String getLessee() { return "承租方"; }
            @Override public String getInvoicePeriod() { return null; }
            @Override public LocalDate getInvoicePeriodStart() { return null; }
            @Override public LocalDate getInvoicePeriodEnd() { return null; }
            @Override public String getClosePlan() { return null; }
            @Override public String getCloseReason() { return null; }
            @Override public Boolean getHasPropertyCert() { return false; }
            @Override public Boolean getHasInvoice() { return false; }
            @Override public Boolean getHasPhotos() { return false; }
            @Override public Boolean getHasLeaseContract() { return false; }
            @Override public String getCertRemarks() { return null; }
            @Override public WarehouseStatus getStatus() { return WarehouseStatus.IN_USE; }
            @Override public Long getCreatedBy() { return 1L; }
            @Override public LocalDateTime getCreatedAt() { return LocalDateTime.now(); }
            @Override public Long getUpdatedBy() { return null; }
            @Override public LocalDateTime getUpdatedAt() { return null; }
        };
    }

    private static WarehouseAttachmentReadModel attachment(String originalFilename) {
        return new WarehouseAttachmentReadModel() {
            @Override public Long getId() { return 1L; }
            @Override public WarehouseAttachmentType getType() { return WarehouseAttachmentType.PHOTOS; }
            @Override public String getOriginalFilename() { return originalFilename; }
            @Override public String getStoredFilename() { return "stored_" + originalFilename; }
            @Override public Long getFileSize() { return 100L; }
            @Override public String getContentType() { return "image/jpeg"; }
            @Override public Long getUploadedBy() { return 1L; }
            @Override public LocalDateTime getUploadedAt() { return LocalDateTime.now(); }
        };
    }
}
