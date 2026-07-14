package com.xiyu.bid.warehouse.infrastructure;

import com.xiyu.bid.warehouse.domain.WarehouseAttachmentType;
import com.xiyu.bid.warehouse.domain.WarehouseReadModel;
import com.xiyu.bid.warehouse.domain.WarehouseStatus;
import com.xiyu.bid.warehouse.domain.WarehouseType;
import com.xiyu.bid.warehouse.domain.WarehouseAttachmentReadModel;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * WarehouseWordBundleBuilder 冒烟测试。
 * <p>
 * 仅验证基础结构：空仓库列表 → 生成可被 POI 重新加载的 docx 字节流。
 * PDF/图片渲染依赖磁盘文件，集成测试复杂度高，不在此覆盖。
 */
class WarehouseWordBundleBuilderTest {

    @TempDir
    Path tempDir;

    @Test
    void buildBundle_emptyWarehouseList_returnsValidDocx() throws IOException {
        WarehouseWordBundleBuilder builder = new WarehouseWordBundleBuilder();
        builder.attachmentRoot = tempDir.toString();

        byte[] result = builder.buildBundle(List.of(), Map.of());

        assertThat(result).isNotEmpty();
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(result))) {
            assertThat(doc.getDocument()).isNotNull();
        }
    }

    @Test
    void buildBundle_warehouseWithNoAttachments_producesNonEmptyDoc() throws IOException {
        WarehouseWordBundleBuilder builder = new WarehouseWordBundleBuilder();
        builder.attachmentRoot = tempDir.toString();

        TestWarehouse wh = new TestWarehouse("杭州仓", "浙江",
                LocalDate.of(2021, 1, 15), LocalDate.of(2029, 1, 14));

        byte[] result = builder.buildBundle(List.of(wh), Map.of());

        assertThat(result).isNotEmpty();
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(result))) {
            assertThat(doc.getDocument()).isNotNull();
        }
    }

    @Test
    void buildBundle_nullEntities_throwsNpe() {
        WarehouseWordBundleBuilder builder = new WarehouseWordBundleBuilder();
        builder.attachmentRoot = tempDir.toString();

        org.junit.jupiter.api.Assertions.assertThrows(NullPointerException.class,
                () -> builder.buildBundle(null, Map.of()));
    }

    @Test
    void buildBundle_sameProvinceMultipleWarehouses_provinceHeadingAppearsOnce() throws IOException {
        WarehouseWordBundleBuilder builder = new WarehouseWordBundleBuilder();
        builder.attachmentRoot = tempDir.toString();

        TestWarehouse wh1 = new TestWarehouse(1L, "杭州仓", "浙江",
                LocalDate.of(2021, 1, 15), LocalDate.of(2029, 1, 14));
        TestWarehouse wh2 = new TestWarehouse(2L, "宁波仓", "浙江",
                LocalDate.of(2022, 3, 1), LocalDate.of(2030, 2, 28));

        byte[] result = builder.buildBundle(List.of(wh1, wh2), Map.of());

        assertThat(result).isNotEmpty();
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(result))) {
            long provinceCount = doc.getParagraphs().stream()
                    .filter(p -> "浙江".equals(p.getText()))
                    .count();
            assertThat(provinceCount)
                    .as("§3.4：同省仓库只输出一次省标题")
                    .isEqualTo(1);
        }
    }

    @Test
    void buildBundle_differentProvinces_eachProvinceHeadingAppearsOnce() throws IOException {
        WarehouseWordBundleBuilder builder = new WarehouseWordBundleBuilder();
        builder.attachmentRoot = tempDir.toString();

        TestWarehouse wh1 = new TestWarehouse(1L, "杭州仓", "浙江",
                LocalDate.of(2021, 1, 15), LocalDate.of(2029, 1, 14));
        TestWarehouse wh2 = new TestWarehouse(2L, "广州仓", "广东",
                LocalDate.of(2022, 3, 1), LocalDate.of(2030, 2, 28));

        byte[] result = builder.buildBundle(List.of(wh1, wh2), Map.of());

        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(result))) {
            long zhejiangCount = doc.getParagraphs().stream()
                    .filter(p -> "浙江".equals(p.getText())).count();
            long guangdongCount = doc.getParagraphs().stream()
                    .filter(p -> "广东".equals(p.getText())).count();
            assertThat(zhejiangCount).isEqualTo(1);
            assertThat(guangdongCount).isEqualTo(1);
        }
    }

    // ========== 测试辅助 ==========

    private static class TestWarehouse implements WarehouseReadModel {
        private final Long id;
        private final String name;
        private final String province;
        private final LocalDate startDate;
        private final LocalDate endDate;

        TestWarehouse(String name, String province, LocalDate startDate, LocalDate endDate) {
            this(1L, name, province, startDate, endDate);
        }

        TestWarehouse(Long id, String name, String province, LocalDate startDate, LocalDate endDate) {
            this.id = id;
            this.name = name;
            this.province = province;
            this.startDate = startDate;
            this.endDate = endDate;
        }

        @Override public Long getId() { return id; }
        @Override public String getName() { return name; }
        @Override public WarehouseType getType() { return WarehouseType.SELF_OPERATED; }
        @Override public String getRegion() { return "华东"; }
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
    }
}
