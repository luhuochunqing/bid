package com.xiyu.bid.common.infrastructure.word;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

/**
 * Word 合订本生成器抽象基类。
 *
 * <p>抽取仓库模块（{@code WarehouseWordBundleBuilder}）与业绩模块
 * （{@code PerformanceWordBundleBuilder}）的公共逻辑：
 * <ul>
 *   <li>文档标题、段落标题、正文文本写入</li>
 *   <li>PDF 高清渲染（逐页 BufferedImage 释放，防 OOM）</li>
 *   <li>图片嵌入（自适应正文宽度）</li>
 *   <li>分页符、文件扩展名解析</li>
 * </ul>
 *
 * <p>子类需实现：
 * <ul>
 *   <li>{@link #getDocumentTitle()} — 文档顶部居中标题文本</li>
 *   <li>{@link #getPdfRenderDpi()} — PDF 渲染 DPI（仓库 96，业绩 300）</li>
 *   <li>{@link #getMaxPdfPages()} — 单 PDF 最大渲染页数（0 表示不限制）</li>
 *   <li>{@link #getContentWidthTwips()} — 正文宽度（twips）</li>
 *   <li>{@link #applyPageSetup(XWPFDocument)} — 页面尺寸与页边距</li>
 *   <li>{@link #registerHeadingStyles(XWPFDocument)} — 标题样式注册</li>
 *   <li>{@link #encodeImage(BufferedImage)} — 图片编码（PNG/JPEG）</li>
 * </ul>
 *
 * <p>受 FP-Java 架构保护：本类只依赖 POI/PDFBox 类型，不依赖 Controller/Repository。
 */
@Slf4j
public abstract class AbstractWordBundleBuilder {

    protected static final String LABEL_NO_ATTACHMENT = "（无附件）";
    protected static final String LABEL_FILE_MISSING = "（文件缺失）";
    protected static final String LABEL_IMAGE_READ_FAILED = "（图片读取失败）";
    protected static final Set<String> IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png");

    /** 1 inch = 72 px（POI 默认 EMU/px 换算） */
    protected static final int PX_PER_INCH = 72;

    // ========== 子类提供的配置 ==========

    /** 文档顶部居中标题文本。 */
    protected abstract String getDocumentTitle();

    /** PDF 渲染 DPI。 */
    protected abstract int getPdfRenderDpi();

    /**
     * 单 PDF 最大渲染页数，0 表示不限制。
     * <p>超过此值的页面将被截断并记录 warn 日志，防止超大 PDF 撑爆堆内存。
     */
    protected abstract int getMaxPdfPages();

    /** 正文宽度（twips），用于图片自适应缩放。 */
    protected abstract int getContentWidthTwips();

    /** 应用页面尺寸与页边距到文档。 */
    protected abstract void applyPageSetup(XWPFDocument doc);

    /** 注册标题样式（Title/Heading1-N）到 word/styles.xml。 */
    protected abstract void registerHeadingStyles(XWPFDocument doc);

    /**
     * 将 BufferedImage 编码为字节数组。
     * <p>子类决定编码格式（PNG 无损 / JPEG 压缩）及对应 pictureType。
     *
     * @return 编码结果（字节数据 + OOXML picture type）
     */
    protected abstract EncodedImage encodeImage(BufferedImage img) throws IOException;

    // ========== 公共方法 ==========

    /** 写入文档标题（Title 样式，居中）。 */
    protected void writeDocumentTitle(XWPFDocument doc) {
        XWPFParagraph p = doc.createParagraph();
        p.setAlignment(ParagraphAlignment.CENTER);
        p.setStyle("Title");
        p.createRun().setText(getDocumentTitle());
    }

    /**
     * 写入标题段落，应用指定 pStyle。
     * <p>字体/字号/加粗/大纲级别由 styles.xml 中的样式定义接管。
     */
    protected void writeHeading(XWPFDocument doc, String text, String styleName) {
        XWPFParagraph p = doc.createParagraph();
        p.setAlignment(ParagraphAlignment.LEFT);
        p.setStyle(styleName);
        p.createRun().setText(text);
    }

    /** 写入正文文本段落。 */
    protected void writeBodyText(XWPFDocument doc, String text) {
        XWPFParagraph p = doc.createParagraph();
        XWPFRun run = p.createRun();
        run.setText(text);
    }

    /**
     * 渲染 PDF 到 Word 文档（逐页转图片嵌入）。
     * <p>逐页渲染后立即 flush BufferedImage，防止大批量导出 OOM。
     * 单 PDF 页数超过 {@link #getMaxPdfPages()} 时自动截断。
     */
    protected void renderPdfToWord(XWPFDocument doc, Path pdfFile) {
        try (PDDocument pdf = PDDocument.load(pdfFile.toFile())) {
            PDFRenderer renderer = new PDFRenderer(pdf);
            int pageCount = pdf.getNumberOfPages();
            int maxPages = getMaxPdfPages();
            int effectivePageCount = (maxPages > 0) ? Math.min(pageCount, maxPages) : pageCount;
            if (maxPages > 0 && pageCount > maxPages) {
                log.warn("PDF 页数超限截断: file={}, totalPages={}, renderedPages={}",
                        pdfFile, pageCount, effectivePageCount);
            }
            for (int i = 0; i < effectivePageCount; i++) {
                try {
                    BufferedImage img = renderer.renderImageWithDPI(i, getPdfRenderDpi());
                    try {
                        boolean inserted = insertImage(doc, img);
                        if (inserted && i < effectivePageCount - 1) {
                            addPageBreak(doc);
                        }
                    } finally {
                        img.flush();
                    }
                } catch (IOException e) {
                    log.warn("PDF 第{}页转换失败: file={}", i + 1, pdfFile, e);
                }
            }
        } catch (IOException e) {
            log.warn("PDF 加载失败: file={}", pdfFile, e);
            writeBodyText(doc, LABEL_FILE_MISSING);
        }
    }

    /**
     * 渲染图片文件到 Word 文档（直接嵌入）。
     * <p>渲染后立即 flush BufferedImage，防止大批量导出 OOM。
     */
    protected void renderImageToWord(XWPFDocument doc, Path imageFile) {
        try {
            BufferedImage img = ImageIO.read(imageFile.toFile());
            if (img == null) {
                writeBodyText(doc, LABEL_IMAGE_READ_FAILED);
                return;
            }
            try {
                insertImage(doc, img);
            } finally {
                img.flush();
            }
        } catch (IOException e) {
            log.warn("图片读取失败: file={}", imageFile, e);
            writeBodyText(doc, LABEL_IMAGE_READ_FAILED);
        }
    }

    /**
     * 将 BufferedImage 嵌入 Word 文档（自适应正文宽度）。
     * <p>编码格式由子类 {@link #encodeImage(BufferedImage)} 决定。
     *
     * @return true 表示嵌入成功
     */
    protected boolean insertImage(XWPFDocument doc, BufferedImage img) {
        try {
            int targetWidthPx = getContentWidthTwips() * PX_PER_INCH / 1440;
            int imgWidth = img.getWidth();
            int imgHeight = img.getHeight();
            int width = Math.min(imgWidth, targetWidthPx);
            int height = imgWidth > 0 ? (int) ((double) imgHeight * width / imgWidth) : imgHeight;

            EncodedImage encoded = encodeImage(img);

            XWPFParagraph p = doc.createParagraph();
            p.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun run = p.createRun();
            run.addPicture(new ByteArrayInputStream(encoded.data()),
                    encoded.pictureType(),
                    encoded.suggestedFilename(),
                    Units.toEMU(width), Units.toEMU(height));
            return true;
        } catch (IOException | org.apache.poi.openxml4j.exceptions.InvalidFormatException e) {
            log.warn("图片插入 Word 失败: {}", e.getMessage(), e);
            writeBodyText(doc, LABEL_IMAGE_READ_FAILED);
            return false;
        }
    }

    /** 添加分页符。 */
    protected void addPageBreak(XWPFDocument doc) {
        XWPFParagraph p = doc.createParagraph();
        XWPFRun run = p.createRun();
        run.addBreak(org.apache.poi.xwpf.usermodel.BreakType.PAGE);
    }

    /**
     * 渲染附件文件到 Word 文档。
     * <p>根据文件扩展名选择 PDF 渲染或图片嵌入。未知格式输出提示文本。
     *
     * @param doc     目标文档
     * @param file    附件文件路径（调用方确保文件存在）
     * @param fileName 原始文件名（用于扩展名解析和日志）
     */
    protected void renderAttachment(XWPFDocument doc, Path file, String fileName) {
        String ext = extractExtension(fileName);
        if ("pdf".equalsIgnoreCase(ext)) {
            renderPdfToWord(doc, file);
        } else if (IMAGE_EXTENSIONS.contains(ext.toLowerCase())) {
            renderImageToWord(doc, file);
        } else {
            log.warn("不支持的附件格式: filename={}, ext={}", fileName, ext);
            writeBodyText(doc, "（不支持的文件格式: " + ext + "）");
        }
    }

    /** 从文件名提取小写扩展名。 */
    protected static String extractExtension(String filename) {
        if (filename == null) return "";
        int dot = filename.lastIndexOf('.');
        return (dot >= 0 && dot < filename.length() - 1)
                ? filename.substring(dot + 1).toLowerCase()
                : "";
    }

    // ========== 编码结果记录 ==========

    /**
     * 图片编码结果。
     *
     * @param data             编码后的字节数据
     * @param pictureType      OOXML picture type（{@link XWPFDocument#PICTURE_TYPE_PNG} 等）
     * @param suggestedFilename 建议的嵌入文件名（如 "image.png"）
     */
    public record EncodedImage(byte[] data, int pictureType, String suggestedFilename) {}
}
