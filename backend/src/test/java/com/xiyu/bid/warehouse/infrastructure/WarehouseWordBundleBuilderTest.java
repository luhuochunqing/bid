package com.xiyu.bid.warehouse.infrastructure;

import com.xiyu.bid.warehouse.domain.WarehouseAttachmentType;
import com.xiyu.bid.warehouse.domain.WarehouseReadModel;
import com.xiyu.bid.warehouse.domain.WarehouseStatus;
import com.xiyu.bid.warehouse.domain.WarehouseType;
import com.xiyu.bid.warehouse.domain.WarehouseAttachmentReadModel;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
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
        ReflectionTestUtils.setField(builder, "attachmentRoot", tempDir.toString());

        byte[] result = builder.buildBundle(List.of(), Map.of());

        assertThat(result).isNotEmpty();
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(result))) {
            assertThat(doc.getDocument()).isNotNull();
        }
    }

    @Test
    void buildBundle_appliesA4PageSettings() throws IOException {
        WarehouseWordBundleBuilder builder = new WarehouseWordBundleBuilder();
        ReflectionTestUtils.setField(builder, "attachmentRoot", tempDir.toString());

        byte[] result = builder.buildBundle(List.of(), Map.of());

        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(result))) {
            var sectPr = doc.getDocument().getBody().getSectPr();
            assertThat(sectPr).isNotNull();
            assertThat(((BigInteger) sectPr.getPgSz().getW()).intValue())
                    .isEqualTo(WarehouseWordStyleConfig.PAGE_WIDTH_TWIPS);
            assertThat(((BigInteger) sectPr.getPgSz().getH()).intValue())
                    .isEqualTo(WarehouseWordStyleConfig.PAGE_HEIGHT_TWIPS);
            assertThat(((BigInteger) sectPr.getPgMar().getTop()).intValue())
                    .isEqualTo(WarehouseWordStyleConfig.MARGIN_TOP_TWIPS);
            assertThat(((BigInteger) sectPr.getPgMar().getLeft()).intValue())
                    .isEqualTo(WarehouseWordStyleConfig.MARGIN_LEFT_TWIPS);
        }
    }

    @Test
    void buildBundle_warehouseWithNoAttachments_producesNonEmptyDoc() throws IOException {
        WarehouseWordBundleBuilder builder = new WarehouseWordBundleBuilder();
        ReflectionTestUtils.setField(builder, "attachmentRoot", tempDir.toString());

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
        ReflectionTestUtils.setField(builder, "attachmentRoot", tempDir.toString());

        org.junit.jupiter.api.Assertions.assertThrows(NullPointerException.class,
                () -> builder.buildBundle(null, Map.of()));
    }

    @Test
    void buildBundle_sameProvinceMultipleWarehouses_provinceHeadingAppearsOnce() throws IOException {
        WarehouseWordBundleBuilder builder = new WarehouseWordBundleBuilder();
        ReflectionTestUtils.setField(builder, "attachmentRoot", tempDir.toString());

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
        ReflectionTestUtils.setField(builder, "attachmentRoot", tempDir.toString());

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

    @Test
    void buildBundle_photoAttachment_noFilenameHeadingInDocument() throws IOException {
        // 准备：临时目录模拟 attachmentRoot/{whId}/
        WarehouseWordBundleBuilder builder = new WarehouseWordBundleBuilder();
        ReflectionTestUtils.setField(builder, "attachmentRoot", tempDir.toString());

        // 准备一张 PNG 图片文件，存到 {whId}/stored.png
        Path whDir = tempDir.resolve("1");
        Files.createDirectories(whDir);
        Path imgPath = whDir.resolve("stored.png");
        BufferedImage img = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        ImageIO.write(img, "png", imgPath.toFile());

        TestWarehouse wh = new TestWarehouse(1L, "杭州仓", "浙江",
                LocalDate.of(2021, 1, 15), LocalDate.of(2029, 1, 14));
        TestAttachment att = new TestAttachment(100L, WarehouseAttachmentType.PHOTOS,
                "原文件名_仓库外景_001.png", "stored.png");

        byte[] result = builder.buildBundle(List.of(wh), Map.of(1L, List.of(att)));

        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(result))) {
            // §3.6：照片直接嵌入，图片顶部不需要添加小标题
            boolean hasFilenameHeading = doc.getParagraphs().stream()
                    .map(XWPFParagraph::getText)
                    .anyMatch(t -> t.contains("原文件名_仓库外景_001"));
            assertThat(hasFilenameHeading)
                    .as("§3.6：照片附件不应输出原文件名小标题")
                    .isFalse();
        }
    }

    @Test
    void buildBundle_pdfAttachment_noPageCaptionInDocument() throws IOException {
        WarehouseWordBundleBuilder builder = new WarehouseWordBundleBuilder();
        ReflectionTestUtils.setField(builder, "attachmentRoot", tempDir.toString());

        // 准备一个最小 PDF 文件（1 页）到 {whId}/lease.pdf
        Path whDir = tempDir.resolve("1");
        Files.createDirectories(whDir);
        Path pdfPath = whDir.resolve("lease.pdf");
        try (PDDocument pdf = new PDDocument()) {
            PDPage page = new PDPage();
            pdf.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(pdf, page)) {
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA, 12);
                cs.newLineAtOffset(100, 700);
                cs.showText("Lease Contract Content");
                cs.endText();
            }
            pdf.save(pdfPath.toFile());
        }

        TestWarehouse wh = new TestWarehouse(1L, "杭州仓", "浙江",
                LocalDate.of(2021, 1, 15), LocalDate.of(2029, 1, 14));
        TestAttachment att = new TestAttachment(100L, WarehouseAttachmentType.LEASE_CONTRACT,
                "WH_杭州仓_租赁合同.pdf", "lease.pdf");

        byte[] result = builder.buildBundle(List.of(wh), Map.of(1L, List.of(att)));

        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(result))) {
            // §3.6：PDF 每页转为图片，嵌入时图片顶部不需要再添加文字说明
            boolean hasPageCaption = doc.getParagraphs().stream()
                    .map(XWPFParagraph::getText)
                    .anyMatch(t -> t.contains("第1页") || t.contains("WH_杭州仓_租赁合同"));
            assertThat(hasPageCaption)
                    .as("§3.6：PDF 附件不应在图片顶部输出文字说明（如 '第1页'、原文件名）")
                    .isFalse();
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

    private static class TestAttachment implements WarehouseAttachmentReadModel {
        private final Long id;
        private final WarehouseAttachmentType type;
        private final String originalFilename;
        private final String storedFilename;

        TestAttachment(Long id, WarehouseAttachmentType type, String originalFilename, String storedFilename) {
            this.id = id;
            this.type = type;
            this.originalFilename = originalFilename;
            this.storedFilename = storedFilename;
        }

        @Override public Long getId() { return id; }
        @Override public WarehouseAttachmentType getType() { return type; }
        @Override public String getOriginalFilename() { return originalFilename; }
        @Override public String getStoredFilename() { return storedFilename; }
        @Override public Long getFileSize() { return 1024L; }
        @Override public String getContentType() { return "application/octet-stream"; }
        @Override public Long getUploadedBy() { return 1L; }
        @Override public LocalDateTime getUploadedAt() { return LocalDateTime.now(); }
    }
}
