package com.xiyu.bid.warehouse.infrastructure;

import com.xiyu.bid.warehouse.domain.WarehouseAttachmentType;
import com.xiyu.bid.warehouse.domain.WarehouseReadModel;
import com.xiyu.bid.warehouse.domain.WarehouseStatus;
import com.xiyu.bid.warehouse.domain.WarehouseType;
import com.xiyu.bid.warehouse.domain.WarehouseAttachmentReadModel;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.util.ReflectionTestUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
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

        byte[] result = buildBundleToBytes(builder, List.of(), Map.of());

        assertThat(result).isNotEmpty();
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(result))) {
            assertThat(doc.getDocument()).isNotNull();
        }
    }

    @Test
    void buildBundle_appliesA4PageSettings() throws IOException {
        WarehouseWordBundleBuilder builder = new WarehouseWordBundleBuilder();
        ReflectionTestUtils.setField(builder, "attachmentRoot", tempDir.toString());

        byte[] result = buildBundleToBytes(builder, List.of(), Map.of());

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

        byte[] result = buildBundleToBytes(builder, List.of(wh), Map.of());

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
                () -> builder.buildBundle(null, Map.of(), new ByteArrayOutputStream()));
    }

    @Test
    void buildBundle_sameProvinceMultipleWarehouses_provinceHeadingAppearsOnce() throws IOException {
        WarehouseWordBundleBuilder builder = new WarehouseWordBundleBuilder();
        ReflectionTestUtils.setField(builder, "attachmentRoot", tempDir.toString());

        TestWarehouse wh1 = new TestWarehouse(1L, "杭州仓", "浙江",
                LocalDate.of(2021, 1, 15), LocalDate.of(2029, 1, 14));
        TestWarehouse wh2 = new TestWarehouse(2L, "宁波仓", "浙江",
                LocalDate.of(2022, 3, 1), LocalDate.of(2030, 2, 28));

        byte[] result = buildBundleToBytes(builder, List.of(wh1, wh2), Map.of());

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

        byte[] result = buildBundleToBytes(builder, List.of(wh1, wh2), Map.of());

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

        byte[] result = buildBundleToBytes(builder, List.of(wh), Map.of(1L, List.of(att)));

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

        byte[] result = buildBundleToBytes(builder, List.of(wh), Map.of(1L, List.of(att)));

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

    // ========== pStyle 标题样式测试（CO-582 §3.4：Word 导航窗格识别层级） ==========

    /**
     * 标题段落必须应用 Word pStyle，让 Word 导航窗格识别层级（CO-582 §3.4）。
     * <p>
     * 根因：原 writeHeading 创建普通段落，仅靠 run 的 bold/fontSize 渲染样式，
     * Word 软件不识别为标题，导航窗格为空，所有标题被当作正文。
     * <p>
     * 修复：writeDocumentTitle 应用 "Title"，省份用 "Heading1"，仓库名用 "Heading2"，
     * 附件分类用 "Heading3"。
     */
    @Test
    void buildBundle_appliesWordHeadingStyles_titleHeading1Heading2Heading3() throws IOException {
        WarehouseWordBundleBuilder builder = new WarehouseWordBundleBuilder();
        ReflectionTestUtils.setField(builder, "attachmentRoot", tempDir.toString());

        // 准备一个最小 PDF 文件，让三级标题"租赁合同(...)"能被生成
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
                cs.showText("Lease");
                cs.endText();
            }
            pdf.save(pdfPath.toFile());
        }

        TestWarehouse wh = new TestWarehouse(1L, "杭州仓", "浙江",
                LocalDate.of(2021, 1, 15), LocalDate.of(2029, 1, 14));
        TestAttachment att = new TestAttachment(100L, WarehouseAttachmentType.LEASE_CONTRACT,
                "WH_杭州仓_租赁合同.pdf", "lease.pdf");

        byte[] result = buildBundleToBytes(builder, List.of(wh), Map.of(1L, List.of(att)));

        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(result))) {
            // 文档标题 "仓库附件合订本" → Title 样式
            String titleStyle = doc.getParagraphs().stream()
                    .filter(p -> "仓库附件合订本".equals(p.getText()))
                    .map(XWPFParagraph::getStyle)
                    .findFirst().orElse(null);
            assertThat(titleStyle)
                    .as("§3.4：文档标题必须应用 Title 样式（让 Word 识别为标题而非正文）")
                    .isEqualTo("Title");

            // 省份一级标题 "浙江" → Heading1
            String provinceStyle = doc.getParagraphs().stream()
                    .filter(p -> "浙江".equals(p.getText()))
                    .map(XWPFParagraph::getStyle)
                    .findFirst().orElse(null);
            assertThat(provinceStyle)
                    .as("§3.4：省份一级标题必须应用 Heading1 样式")
                    .isEqualTo("Heading1");

            // 仓库名二级标题 "杭州仓" → Heading2
            String warehouseStyle = doc.getParagraphs().stream()
                    .filter(p -> "杭州仓".equals(p.getText()))
                    .map(XWPFParagraph::getStyle)
                    .findFirst().orElse(null);
            assertThat(warehouseStyle)
                    .as("§3.4：仓库名二级标题必须应用 Heading2 样式")
                    .isEqualTo("Heading2");

            // 附件分类三级标题 "租赁合同(...)" → Heading3
            String sectionStyle = doc.getParagraphs().stream()
                    .filter(p -> p.getText() != null && p.getText().startsWith("租赁合同"))
                    .map(XWPFParagraph::getStyle)
                    .findFirst().orElse(null);
            assertThat(sectionStyle)
                    .as("§3.4：附件分类三级标题必须应用 Heading3 样式")
                    .isEqualTo("Heading3");
        }
    }

    /**
     * 强断言：styles.xml 必须真正定义 Title/Heading1/Heading2/Heading3 样式 + outlineLvl。
     * <p>
     * 根因（CO-582 §3.4 彻底修复）：
     * 上一版修复只调用 {@code p.setStyle("Heading1")}，但 POI 的 {@code new XWPFDocument()}
     * 默认不生成 word/styles.xml，Word 打开后找不到样式定义，导航窗格仍为空。
     * 上一版的 {@code buildBundle_appliesWordHeadingStyles_*} 测试只断言 {@code p.getStyle()}
     * 返回的字符串，无法捕获此 bug——getStyle 只读段落上的 pStyle ID，不校验 styles.xml。
     * <p>
     * 本测试补强：
     * <ol>
     *   <li>{@code doc.getStyles()} 不为 null</li>
     *   <li>四个样式 ID 在 styles.xml 中都有定义</li>
     *   <li>每个样式的 {@code outlineLvl} 值正确（Title=0, H1=0, H2=1, H3=2）</li>
     *   <li>每个样式有 {@code qFormat} 标记</li>
     * </ol>
     * 防止以后任何人删掉 {@link WarehouseWordStyleRegistrar#registerHeadingStyles} 调用
     * 而其他弱断言测试继续"假绿"放行。
     */
    @Test
    void buildBundle_stylesXmlDefinesHeadingStylesWithOutlineLevel() throws IOException {
        WarehouseWordBundleBuilder builder = new WarehouseWordBundleBuilder();
        ReflectionTestUtils.setField(builder, "attachmentRoot", tempDir.toString());

        // 准备一个最小 PDF，让三级标题"租赁合同(...)"能被生成
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
                cs.showText("Lease");
                cs.endText();
            }
            pdf.save(pdfPath.toFile());
        }

        TestWarehouse wh = new TestWarehouse(1L, "杭州仓", "浙江",
                LocalDate.of(2021, 1, 15), LocalDate.of(2029, 1, 14));
        TestAttachment att = new TestAttachment(100L, WarehouseAttachmentType.LEASE_CONTRACT,
                "WH_杭州仓_租赁合同.pdf", "lease.pdf");

        byte[] result = buildBundleToBytes(builder, List.of(wh), Map.of(1L, List.of(att)));

        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(result))) {
            // 1. styles 部分必须存在（POI new XWPFDocument() 默认不带）
            org.apache.poi.xwpf.usermodel.XWPFStyles styles = doc.getStyles();
            assertThat(styles)
                    .as("§3.4 根因修复：styles.xml 必须存在（POI 默认不生成）")
                    .isNotNull();

            // 2. 四个标题样式必须都被定义
            assertStyleDefinedWithOutlineLvl(styles, "Title", 0);
            assertStyleDefinedWithOutlineLvl(styles, "Heading1", 0);
            assertStyleDefinedWithOutlineLvl(styles, "Heading2", 1);
            assertStyleDefinedWithOutlineLvl(styles, "Heading3", 2);
        }
    }

    /**
     * 断言指定 styleId 的样式在 styles.xml 中存在，且 outlineLvl/qFormat 正确。
     */
    private static void assertStyleDefinedWithOutlineLvl(
            org.apache.poi.xwpf.usermodel.XWPFStyles styles,
            String styleId, int expectedOutlineLvl) {
        org.apache.poi.xwpf.usermodel.XWPFStyle style = styles.getStyle(styleId);
        assertThat(style)
                .as("§3.4 根因修复：样式 %s 必须在 styles.xml 中定义（不能只靠段落 pStyle ID）", styleId)
                .isNotNull();

        org.openxmlformats.schemas.wordprocessingml.x2006.main.CTStyle ctStyle = style.getCTStyle();
        assertThat(ctStyle.isSetQFormat())
                .as("§3.4 根因修复：样式 %s 必须有 qFormat 标记（让 Word 识别为快速样式）", styleId)
                .isTrue();

        assertThat(ctStyle.isSetPPr())
                .as("§3.4 根因修复：样式 %s 必须有 pPr 段落属性", styleId)
                .isTrue();
        assertThat(ctStyle.getPPr().isSetOutlineLvl())
                .as("§3.4 根因修复：样式 %s 必须有 outlineLvl（Word 导航窗格识别层级的唯一依据）", styleId)
                .isTrue();
        BigInteger actualLvl = ctStyle.getPPr().getOutlineLvl().getVal();
        assertThat(actualLvl.intValue())
                .as("§3.4 根因修复：样式 %s 的 outlineLvl 必须为 %d", styleId, expectedOutlineLvl)
                .isEqualTo(expectedOutlineLvl);
    }

    // ========== 根因行为测试（CO-582 bug：macOS SSV 只读 + 绝对路径默认值） ==========

    /**
     * 根因行为测试 1：验证 attachmentRoot 的 @Value 默认值是相对路径，不能以 "/" 开头。
     * <p>
     * 根因：原默认值 "/data/attachments/warehouse" 是绝对路径，在 macOS 上根目录 "/" 是只读文件系统
     * （SSV 保护），mkdir /data 失败，导致上传文件无法写入，Word 生成时 Files.exists 返回 false，
     * 所有三级标题下显示"（文件缺失）"。
     * <p>
     * 修复：默认值改为 "data/warehouse-attachments"（相对路径，对齐 personnel/qualification 模块约定）。
     * <p>
     * 防回归：本测试扫描 warehouse 模块所有 @Value("${*.attachment.root:...}") 注解，
     * 确保默认值不以 "/" 开头，避免再次踩坑。
     */
    @Test
    void attachmentRootDefault_mustBeRelativePath_notAbsolute() throws Exception {
        // WarehouseWordBundleBuilder
        assertRelativePathDefault(WarehouseWordBundleBuilder.class, "attachmentRoot");
        // WarehouseExportZipBuilder
        assertRelativePathDefault(WarehouseExportZipBuilder.class, "attachmentRoot");
        // WarehouseFileService
        assertRelativePathDefault(com.xiyu.bid.warehouse.file.WarehouseFileService.class, "rootPath");
        // WarehouseImportAttachmentProcessor
        assertRelativePathDefault(com.xiyu.bid.warehouse.application.WarehouseImportAttachmentProcessor.class, "attachmentRoot");
        // PerformanceAttachmentStorageAppService
        assertRelativePathDefault(com.xiyu.bid.performance.application.service.PerformanceAttachmentStorageAppService.class, "attachmentRoot");
        // PerformanceImportAttachmentProcessor
        assertRelativePathDefault(com.xiyu.bid.performance.application.service.PerformanceImportAttachmentProcessor.class, "attachmentRoot");
    }

    /**
     * 根因行为测试 2：端到端验证"上传→生成 Word→内容完整"链路。
     * <p>
     * 验证：当附件文件真实存在于 attachmentRoot/{whId}/{storedFilename} 时，
     * 生成的 Word 中必须包含图片内容（不是"（文件缺失）"标注）。
     * <p>
     * 这覆盖了原 bug 的根因行为：Files.exists 必须在文件存在时返回 true。
     */
    @Test
    void buildBundle_pdfAttachmentFileExists_wordContainsImageNotMissingLabel() throws IOException {
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

        byte[] result = buildBundleToBytes(builder, List.of(wh), Map.of(1L, List.of(att)));

        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(result))) {
            // 根因行为验证：文件存在时，Word 中必须不包含"（文件缺失）"标注
            boolean hasMissingLabel = doc.getParagraphs().stream()
                    .map(XWPFParagraph::getText)
                    .anyMatch("（文件缺失）"::equals);
            assertThat(hasMissingLabel)
                    .as("根因行为：附件文件存在时，Word 中不应出现'（文件缺失）'标注")
                    .isFalse();

            // 根因行为验证：Word 中必须包含至少一个图片（PDF 转图片嵌入成功）
            boolean hasImage = doc.getParagraphs().stream()
                    .flatMap(p -> p.getRuns().stream())
                    .map(XWPFRun::getEmbeddedPictures)
                    .anyMatch(pics -> pics != null && !pics.isEmpty());
            assertThat(hasImage)
                    .as("根因行为：PDF 附件存在时，Word 中应包含嵌入的图片")
                    .isTrue();
        }
    }

    /**
     * 根因行为测试 3：验证文件不存在时仍然降级显示"（文件缺失）"（保护降级语义）。
     */
    @Test
    void buildBundle_pdfAttachmentFileMissing_wordShowsMissingLabel() throws IOException {
        WarehouseWordBundleBuilder builder = new WarehouseWordBundleBuilder();
        // 故意指向空目录，文件不存在
        ReflectionTestUtils.setField(builder, "attachmentRoot", tempDir.toString());

        TestWarehouse wh = new TestWarehouse(1L, "杭州仓", "浙江",
                LocalDate.of(2021, 1, 15), LocalDate.of(2029, 1, 14));
        // 不在磁盘上创建 lease.pdf，模拟文件丢失
        TestAttachment att = new TestAttachment(100L, WarehouseAttachmentType.LEASE_CONTRACT,
                "WH_杭州仓_租赁合同.pdf", "lease.pdf");

        byte[] result = buildBundleToBytes(builder, List.of(wh), Map.of(1L, List.of(att)));

        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(result))) {
            boolean hasMissingLabel = doc.getParagraphs().stream()
                    .map(XWPFParagraph::getText)
                    .anyMatch("（文件缺失）"::equals);
            assertThat(hasMissingLabel)
                    .as("降级语义：附件文件丢失时，Word 应显示'（文件缺失）'标注")
                    .isTrue();
        }
    }

    /**
     * 辅助：调用 buildBundle 并返回 byte[]（测试便捷方法）。
     */
    private byte[] buildBundleToBytes(WarehouseWordBundleBuilder builder,
                                       List<? extends WarehouseReadModel> entities,
                                       Map<Long, ? extends List<? extends WarehouseAttachmentReadModel>> attachments) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        builder.buildBundle(entities, attachments, out);
        return out.toByteArray();
    }

    /**
     * 辅助：断言指定类的指定字段上的 @Value 注解默认值是相对路径（不以 "/" 开头）。
     */
    private static void assertRelativePathDefault(Class<?> clazz, String fieldName) throws Exception {
        java.lang.reflect.Field field = clazz.getDeclaredField(fieldName);
        Value annotation = field.getAnnotation(Value.class);
        assertThat(annotation)
                .as("@Value 注解必须存在: %s.%s", clazz.getSimpleName(), fieldName)
                .isNotNull();
        String valueExpr = annotation.value();
        assertThat(valueExpr)
                .as("@Value 表达式必须包含默认值: %s.%s", clazz.getSimpleName(), fieldName)
                .contains(":");
        int colonIdx = valueExpr.indexOf(':');
        String defaultValue = valueExpr.substring(colonIdx + 1, valueExpr.length() - 1);
        assertThat(defaultValue)
                .as("根因防护：默认值不能是绝对路径（macOS SSV 只读），%s.%s = %s",
                        clazz.getSimpleName(), fieldName, defaultValue)
                .doesNotStartWith("/");
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
