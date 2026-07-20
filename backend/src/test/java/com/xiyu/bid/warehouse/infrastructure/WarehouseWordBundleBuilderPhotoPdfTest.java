package com.xiyu.bid.warehouse.infrastructure;

import com.xiyu.bid.warehouse.domain.WarehouseAttachmentReadModel;
import com.xiyu.bid.warehouse.domain.WarehouseAttachmentType;
import com.xiyu.bid.warehouse.domain.WarehouseReadModel;
import com.xiyu.bid.warehouse.domain.WarehouseStatus;
import com.xiyu.bid.warehouse.domain.WarehouseType;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
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
 * CO-582 修复后新增逻辑路径测试：内外照片附件为 PDF 扫描件时，
 * {@link WarehouseWordBundleBuilder} 应能将其渲染到 Word 中。
 *
 * <p>原 {@code writePhotos()} 仅接受 jpg/jpeg/png，所有 PDF 形式的内外照片被跳过，
 * 导致导出的合订本三级标题下文件缺失。修复后增加了 PDF 分支，本测试覆盖该分支。
 */
class WarehouseWordBundleBuilderPhotoPdfTest {

    @TempDir
    Path tempDir;

    @Test
    void buildBundle_photoPdfAttachment_rendersPdfIntoWord() throws IOException {
        WarehouseWordBundleBuilder builder = new WarehouseWordBundleBuilder();
        ReflectionTestUtils.setField(builder, "attachmentRoot", tempDir.toString());

        Path whDir = tempDir.resolve("1");
        Files.createDirectories(whDir);
        Path pdfPath = whDir.resolve("photo_scan.pdf");
        try (PDDocument pdf = new PDDocument()) {
            PDPage page = new PDPage();
            pdf.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(pdf, page)) {
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA, 12);
                cs.newLineAtOffset(100, 700);
                cs.showText("Photo Scan");
                cs.endText();
            }
            pdf.save(pdfPath.toFile());
        }

        TestWarehouse wh = new TestWarehouse(1L, "杭州仓", "浙江",
                LocalDate.of(2021, 1, 15), LocalDate.of(2029, 1, 14));
        TestAttachment att = new TestAttachment(100L, WarehouseAttachmentType.PHOTOS,
                "仓库外景_扫描件.pdf", "photo_scan.pdf");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        builder.buildBundle(List.of(wh), Map.of(1L, List.of(att)), out);
        byte[] result = out.toByteArray();

        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(result))) {
            // 文件存在时不应出现缺失标注
            boolean hasMissingLabel = doc.getParagraphs().stream()
                    .map(p -> p.getText())
                    .anyMatch("（文件缺失）"::equals);
            assertThat(hasMissingLabel)
                    .as("PDF 扫描件照片存在时，Word 中不应出现'（文件缺失）'标注")
                    .isFalse();

            // PDF 应被转为图片嵌入
            boolean hasImage = doc.getParagraphs().stream()
                    .flatMap(p -> p.getRuns().stream())
                    .map(XWPFRun::getEmbeddedPictures)
                    .anyMatch(pics -> pics != null && !pics.isEmpty());
            assertThat(hasImage)
                    .as("PDF 扫描件照片应被渲染为图片嵌入 Word")
                    .isTrue();
        }
    }

    private static class TestWarehouse implements WarehouseReadModel {
        private final Long id;
        private final String name;
        private final String province;
        private final LocalDate startDate;
        private final LocalDate endDate;

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
        @Override public String getContentType() { return "application/pdf"; }
        @Override public Long getUploadedBy() { return 1L; }
        @Override public LocalDateTime getUploadedAt() { return LocalDateTime.now(); }
    }
}
