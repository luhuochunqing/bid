package com.xiyu.bid.warehouse.infrastructure;

import com.xiyu.bid.common.infrastructure.word.AbstractWordBundleBuilder;
import com.xiyu.bid.warehouse.domain.WarehouseAttachmentOrganizationForm;
import com.xiyu.bid.warehouse.domain.WarehouseAttachmentReadModel;
import com.xiyu.bid.warehouse.domain.WarehouseAttachmentType;
import com.xiyu.bid.warehouse.domain.WarehouseReadModel;
import com.xiyu.bid.warehouse.domain.WarehouseWordBundleOrganizationPolicy;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 仓库 Word 合订本生成器（CO-582 §3.4-§3.9）。
 *
 * <p>业务规则由 {@link WarehouseWordBundleOrganizationPolicy} 提供，
 * 样式由 {@link WarehouseWordStyleConfig} 提供。
 *
 * <p>公共逻辑（文档标题/段落标题/PDF 渲染/图片嵌入/分页符等）继承自
 * {@link AbstractWordBundleBuilder}，本类仅实现仓库模块特有的配置与业务逻辑。
 */
@Component
@Slf4j
public class WarehouseWordBundleBuilder extends AbstractWordBundleBuilder {

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
            applyPageSetup(doc);
            registerHeadingStyles(doc);
            writeDocumentTitle(doc);

            // §3.4：省份为分组层级，同省仓库只输出一次省标题
            String lastProvince = null;
            for (WarehouseReadModel wh : sorted) {
                String province = wh.getProvince();
                if (province != null && !province.equals(lastProvince)) {
                    writeHeading(doc, province, "Heading1");
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

    // ========== 子类配置实现 ==========

    @Override
    protected String getDocumentTitle() {
        return "仓库附件合订本";
    }

    @Override
    protected int getPdfRenderDpi() {
        return WarehouseWordStyleConfig.PDF_RENDER_DPI;
    }

    @Override
    protected int getMaxPdfPages() {
        return 0;  // 仓库模块不限制 PDF 页数
    }

    @Override
    protected int getContentWidthTwips() {
        return WarehouseWordStyleConfig.CONTENT_WIDTH_TWIPS;
    }

    @Override
    protected void applyPageSetup(XWPFDocument doc) {
        WarehouseWordBundlePageSetup.applyTo(doc);
    }

    @Override
    protected void registerHeadingStyles(XWPFDocument doc) {
        WarehouseWordStyleRegistrar.registerHeadingStyles(doc);
    }

    /**
     * 将 BufferedImage 编码为 PNG 无损字节数组。
     * <p>仓库模块使用 PNG 无损编码，保证产权证/发票等文件清晰度。
     */
    @Override
    protected EncodedImage encodeImage(BufferedImage img) throws IOException {
        ByteArrayOutputStream imgOut = new ByteArrayOutputStream();
        ImageIO.write(img, "png", imgOut);
        return new EncodedImage(
                imgOut.toByteArray(),
                XWPFDocument.PICTURE_TYPE_PNG,
                "image.png"
        );
    }

    // ========== 仓库段落（二级标题 Heading2） ==========

    private void writeWarehouseSection(XWPFDocument doc, WarehouseReadModel wh,
                                        List<? extends WarehouseAttachmentReadModel> attachments) {
        writeHeading(doc, wh.getName(), "Heading2");

        if (attachments.isEmpty()) {
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

            // 三级标题：附件分类（应用 Heading3 样式）
            String sectionTitle = WarehouseWordBundleOrganizationPolicy.wordSectionTitle(type, wh);
            writeHeading(doc, sectionTitle, "Heading3");

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
                    img.flush();
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
}
