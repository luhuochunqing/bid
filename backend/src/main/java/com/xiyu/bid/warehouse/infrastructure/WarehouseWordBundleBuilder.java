package com.xiyu.bid.warehouse.infrastructure;

import com.xiyu.bid.warehouse.domain.WarehouseAttachmentOrganizationForm;
import com.xiyu.bid.warehouse.domain.WarehouseAttachmentReadModel;
import com.xiyu.bid.warehouse.domain.WarehouseAttachmentType;
import com.xiyu.bid.warehouse.domain.WarehouseReadModel;
import com.xiyu.bid.warehouse.domain.WarehouseWordBundleOrganizationPolicy;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 仓库 Word 合订本生成器（CO-582 §3.4-§3.9）。
 * 业务规则由 {@link WarehouseWordBundleOrganizationPolicy} 提供，样式由 {@link WarehouseWordStyleConfig} 提供。
 * 异常处理（§4）：无附件/文件缺失/图片读取失败/PDF 转换失败/Word 整体失败各有对应降级。
 */
@Component
@Slf4j
public class WarehouseWordBundleBuilder {

    /** §4 异常标注 */
    private static final String LABEL_NO_ATTACHMENT = "（无附件）";
    private static final String LABEL_FILE_MISSING = "（文件缺失）";
    private static final String LABEL_IMAGE_READ_FAILED = "（图片读取失败）";

    /** 图片格式白名单（§3.7.2） */
    private static final Set<String> IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png");

    @Value("${warehouse.attachment.root:data/warehouse-attachments}")
    private String attachmentRoot;

    /** 生成 Word 合订本并写入输出流；单个附件失败不影响整体。 */
    public void buildBundle(List<? extends WarehouseReadModel> entities,
                              Map<Long, ? extends List<? extends WarehouseAttachmentReadModel>> attachmentsByWhId,
                              OutputStream out) {
        Objects.requireNonNull(entities, "entities");
        Objects.requireNonNull(attachmentsByWhId, "attachmentsByWhId");
        Objects.requireNonNull(out, "out");

        // 排序：省份 → 仓库名（拼音字典序）
        List<? extends WarehouseReadModel> sorted = WarehouseWordBundleOrganizationPolicy.sortByProvinceThenName(entities);

        try (XWPFDocument doc = new XWPFDocument()) {

            // §3.9：页面尺寸与页边距
            WarehouseWordBundlePageSetup.applyTo(doc);

            // 文档标题（§3.9：居中，黑体 18pt，加粗）
            writeDocumentTitle(doc);

            // §3.4：省份为分组层级，同省仓库只输出一次省标题
            String lastProvince = null;
            for (WarehouseReadModel wh : sorted) {
                String province = wh.getProvince();
                if (province != null && !province.equals(lastProvince)) {
                    writeProvinceHeading(doc, province);
                    lastProvince = province;
                }
                List<? extends WarehouseAttachmentReadModel> attachments = attachmentsByWhId.get(wh.getId());
                if (attachments == null) {
                    attachments = List.of();
                }
                writeWarehouseSection(doc, wh, attachments);
            }

            doc.write(out);
        } catch (IOException e) {
            throw new RuntimeException("Word 合订本生成失败: " + e.getMessage(), e);
        }
    }

    // ========== 文档标题 ==========

    private void writeDocumentTitle(XWPFDocument doc) {
        XWPFParagraph p = doc.createParagraph();
        p.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun run = p.createRun();
        run.setText("仓库附件合订本");
        run.setFontFamily(WarehouseWordStyleConfig.FONT_HEITI);
        run.setFontSize(WarehouseWordStyleConfig.SIZE_TITLE_PT);
        run.setBold(true);
    }

    // ========== 省份一级标题（§3.4：同省仓库合并到同一个省标题下） ==========

    private void writeProvinceHeading(XWPFDocument doc, String province) {
        writeHeading(doc, province,
                WarehouseWordStyleConfig.FONT_HEITI, WarehouseWordStyleConfig.SIZE_H1_PT, true);
    }

    // ========== 仓库段落（二级标题：仓库名） ==========

    private void writeWarehouseSection(XWPFDocument doc, WarehouseReadModel wh,
                                        List<? extends WarehouseAttachmentReadModel> attachments) {
        // 二级标题：仓库名（§3.9：左对齐，黑体 14pt，加粗）
        writeHeading(doc, wh.getName(),
                WarehouseWordStyleConfig.FONT_HEITI, WarehouseWordStyleConfig.SIZE_H2_PT, true);

        if (attachments.isEmpty()) {
            // §4：仓库无附件 → 标注"（无附件）"
            writeBodyText(doc, LABEL_NO_ATTACHMENT);
            return;
        }

        // 按固定分类顺序遍历
        for (WarehouseAttachmentType type : WarehouseWordBundleOrganizationPolicy.ATTACHMENT_TYPE_ORDER) {
            List<? extends WarehouseAttachmentReadModel> typeAttachments = attachments.stream()
                    .filter(a -> a.getType() == type)
                    .toList();
            if (typeAttachments.isEmpty()) {
                continue;  // §3.6：无某类附件则不输出标题
            }

            // 三级标题：附件分类
            String sectionTitle = WarehouseWordBundleOrganizationPolicy.wordSectionTitle(type, wh);
            writeHeading(doc, sectionTitle,
                    WarehouseWordStyleConfig.FONT_SONGTI, WarehouseWordStyleConfig.SIZE_H3_PT, true);

            // 附件内容
            if (type == WarehouseAttachmentType.PHOTOS) {
                writePhotos(doc, typeAttachments, wh);
            } else {
                writePdfAttachments(doc, typeAttachments, wh);
            }
        }
    }

    // ========== PDF 附件 ==========

    private void writePdfAttachments(XWPFDocument doc, List<? extends WarehouseAttachmentReadModel> attachments,
                                      WarehouseReadModel wh) {
        for (WarehouseAttachmentReadModel att : attachments) {
            Path file = resolveAttachmentPath(wh, att);
            if (!Files.exists(file)) {
                log.warn("附件文件不存在（PDF）: warehouseId={}, attachmentId={}, storedFilename={}, resolvedPath={}, attachmentRoot={}",
                        wh.getId(), att.getId(), att.getStoredFilename(), file.toAbsolutePath(), attachmentRoot);
                writeBodyText(doc, LABEL_FILE_MISSING);
                continue;
            }
            renderPdfToWord(doc, file);
        }
    }

    private void renderPdfToWord(XWPFDocument doc, Path pdfFile) {
        try (PDDocument pdf = PDDocument.load(pdfFile.toFile())) {
            PDFRenderer renderer = new PDFRenderer(pdf);
            int pageCount = pdf.getNumberOfPages();
            for (int i = 0; i < pageCount; i++) {
                try {
                    BufferedImage img = renderer.renderImageWithDPI(i, WarehouseWordStyleConfig.PDF_RENDER_DPI);
                    try {
                        // §3.6：PDF 嵌入时不添加文字说明（如 "第N页"、原文件名）
                        boolean inserted = insertImage(doc, img);
                        if (inserted && i < pageCount - 1) {
                            addPageBreak(doc);
                        }
                    } finally {
                        img.flush();  // 释放内存，防止大批量导出 OOM
                    }
                } catch (IOException e) {
                    log.warn("PDF 第{}页转换失败: file={}", i + 1, pdfFile, e);
                    // §4：跳过该页，继续后续页
                }
            }
        } catch (IOException e) {
            log.warn("PDF 加载失败: file={}", pdfFile, e);
            writeBodyText(doc, LABEL_FILE_MISSING);
        }
    }

    // ========== 照片附件 ==========

    private void writePhotos(XWPFDocument doc, List<? extends WarehouseAttachmentReadModel> attachments,
                              WarehouseReadModel wh) {
        // §3.5：照片按原文件名升序
        List<? extends WarehouseAttachmentReadModel> sorted =
                WarehouseWordBundleOrganizationPolicy.sortAttachmentsByFilename(attachments);

        for (WarehouseAttachmentReadModel att : sorted) {
            String ext = extractExtension(att.getOriginalFilename());
            Path file = resolveAttachmentPath(wh, att);
            if (!Files.exists(file)) {
                log.warn("附件文件不存在（照片）: warehouseId={}, attachmentId={}, storedFilename={}, resolvedPath={}, attachmentRoot={}",
                        wh.getId(), att.getId(), att.getStoredFilename(), file.toAbsolutePath(), attachmentRoot);
                writeBodyText(doc, LABEL_FILE_MISSING);
                continue;
            }
            // PDF 走 PDF 渲染逻辑（每页转图片嵌入），支持扫描件 PDF 形式的内外照片
            if ("pdf".equalsIgnoreCase(ext)) {
                renderPdfToWord(doc, file);
                continue;
            }
            if (!IMAGE_EXTENSIONS.contains(ext)) {
                log.warn("不支持的照片格式: filename={}", att.getOriginalFilename());
                writeBodyText(doc, LABEL_IMAGE_READ_FAILED);
                continue;
            }
            try {
                BufferedImage img = ImageIO.read(file.toFile());
                if (img == null) {
                    writeBodyText(doc, LABEL_IMAGE_READ_FAILED);
                    continue;
                }
                try {
                    insertImage(doc, img);
                    // §3.7.2：照片按自然流式排版跨页，不再强制分页
                } finally {
                    img.flush();  // 释放内存，防止大批量导出 OOM
                }
            } catch (IOException e) {
                log.warn("图片读取失败: file={}", file, e);
                writeBodyText(doc, LABEL_IMAGE_READ_FAILED);
            }
        }
    }

    // ========== 辅助方法 ==========

    private Path resolveAttachmentPath(WarehouseReadModel wh, WarehouseAttachmentReadModel att) {
        return Paths.get(attachmentRoot, String.valueOf(wh.getId()), att.getStoredFilename());
    }

    private void writeHeading(XWPFDocument doc, String text, String font, int sizePt, boolean bold) {
        XWPFParagraph p = doc.createParagraph();
        p.setAlignment(ParagraphAlignment.LEFT);
        XWPFRun run = p.createRun();
        run.setText(text);
        run.setFontFamily(font);
        run.setFontSize(sizePt);
        run.setBold(bold);
    }

    private void writeBodyText(XWPFDocument doc, String text) {
        XWPFParagraph p = doc.createParagraph();
        XWPFRun run = p.createRun();
        run.setText(text);
    }

    private boolean insertImage(XWPFDocument doc, BufferedImage img) {
        try {
            // 图片宽度自适应正文宽度
            int targetWidthPx = WarehouseWordStyleConfig.CONTENT_WIDTH_TWIPS
                    * WarehouseWordStyleConfig.PX_PER_INCH / 1440;
            int imgWidth = img.getWidth();
            int imgHeight = img.getHeight();
            int width = Math.min(imgWidth, targetWidthPx);
            int height = imgWidth > 0 ? (int) ((double) imgHeight * width / imgWidth) : imgHeight;

            XWPFParagraph p = doc.createParagraph();
            p.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun run = p.createRun();
            ByteArrayOutputStream imgOut = new ByteArrayOutputStream();
            ImageIO.write(img, "png", imgOut);
            run.addPicture(new ByteArrayInputStream(imgOut.toByteArray()),
                    XWPFDocument.PICTURE_TYPE_PNG,
                    "image.png",
                    Units.toEMU(width), Units.toEMU(height));
            return true;
        } catch (IOException | org.apache.poi.openxml4j.exceptions.InvalidFormatException | RuntimeException e) {
            log.warn("图片插入 Word 失败: {}", e.getMessage());
            writeBodyText(doc, LABEL_IMAGE_READ_FAILED);
            return false;
        }
    }

    private void addPageBreak(XWPFDocument doc) {
        XWPFParagraph p = doc.createParagraph();
        XWPFRun run = p.createRun();
        run.addBreak(org.apache.poi.xwpf.usermodel.BreakType.PAGE);
    }

    private static String extractExtension(String filename) {
        if (filename == null) return "";
        int dot = filename.lastIndexOf('.');
        return (dot >= 0 && dot < filename.length() - 1)
                ? filename.substring(dot + 1).toLowerCase()
                : "";
    }
}
