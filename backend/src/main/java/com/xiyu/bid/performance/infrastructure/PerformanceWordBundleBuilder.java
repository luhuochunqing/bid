package com.xiyu.bid.performance.infrastructure;

import com.xiyu.bid.common.infrastructure.word.AbstractWordBundleBuilder;
import com.xiyu.bid.performance.application.AttachmentPathResolver;
import com.xiyu.bid.performance.application.dto.PerformanceDTO;
import com.xiyu.bid.performance.domain.valueobject.CustomerType;
import com.xiyu.bid.performance.infrastructure.PerformanceBundleGroupTypes.AttachmentTypeGroup;
import com.xiyu.bid.performance.infrastructure.PerformanceBundleGroupTypes.ContractGroup;
import com.xiyu.bid.performance.infrastructure.PerformanceBundleGroupTypes.CustomerTypeGroup;
import com.xiyu.bid.performance.infrastructure.PerformanceBundleGroupTypes.GroupCompanyGroup;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 业绩合订本 Word 生成器。
 *
 * <p>四级层级结构（需求 §1）：
 * <pre>
 * H1 客户类型 → H2 集团名称 → H3 合同名称+附件类型 → H4 中标通知书
 * </pre>
 *
 * <p>高清渲染（需求 §2）：PDF 使用 300 DPI 渲染，JPEG 0.85 编码，保证源文件清晰度不被压缩。
 *
 * <p>央企共享优化（需求 §3）：由 {@link PerformanceWordBundleOrganizationPolicy} 在分组阶段去重。
 *
 * <p>错误恢复：单个附件读取/渲染失败不影响整体，标注"（文件缺失）"继续。
 *
 * <p>公共逻辑（文档标题/段落标题/PDF 渲染/图片嵌入/分页符等）继承自
 * {@link AbstractWordBundleBuilder}，本类仅实现业绩模块特有的配置与业务逻辑。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PerformanceWordBundleBuilder extends AbstractWordBundleBuilder {

    /** 附件路径解析服务（复用已有逻辑，支持绝对路径与批量导入相对路径） */
    private final AttachmentPathResolver attachmentPathResolver;

    /**
     * 生成业绩合订本 Word 文档并写入输出流。
     *
     * @param records         业绩记录列表
     * @param attachmentTypes 要导出的附件类型集合；null 或空 = 全部
     * @param out             输出流
     */
    public void buildBundle(List<PerformanceDTO> records,
                             Set<String> attachmentTypes,
                             OutputStream out) {
        Objects.requireNonNull(records, "records");
        Objects.requireNonNull(out, "out");

        List<CustomerTypeGroup> hierarchy =
                PerformanceWordBundleOrganizationPolicy.groupByHierarchy(records, attachmentTypes);

        try (XWPFDocument doc = new XWPFDocument()) {
            applyPageSetup(doc);
            registerHeadingStyles(doc);
            writeDocumentTitle(doc);

            for (CustomerTypeGroup ctGroup : hierarchy) {
                writeCustomerTypeHeading(doc, ctGroup.customerType());
                for (GroupCompanyGroup gcGroup : ctGroup.groups()) {
                    writeGroupCompanyHeading(doc, gcGroup.groupCompany());
                    // 先写合同段落（含合同协议、中标通知书等合同级别附件）
                    for (ContractGroup contract : gcGroup.contracts()) {
                        writeContractSection(doc, contract);
                    }
                    // 央企：再写集团级别共享附件（去重后的关系证明/央企名录/品类页/商城截图）
                    for (AttachmentTypeGroup sharedAtt : gcGroup.sharedAttachments()) {
                        writeHeading(doc, sharedAtt.label(), "Heading3");
                        writeAttachments(doc, sharedAtt.attachments());
                    }
                }
            }

            doc.write(out);
        } catch (IOException e) {
            throw new RuntimeException("业绩合订本生成失败: " + e.getMessage(), e);
        }
    }

    // ========== 子类配置实现 ==========

    @Override
    protected String getDocumentTitle() {
        return "业绩合订本";
    }

    @Override
    protected int getPdfRenderDpi() {
        return PerformanceWordStyleConfig.PDF_RENDER_DPI;
    }

    @Override
    protected int getMaxPdfPages() {
        return PerformanceWordStyleConfig.MAX_PDF_PAGES_PER_FILE;
    }

    @Override
    protected int getContentWidthTwips() {
        return PerformanceWordStyleConfig.CONTENT_WIDTH_TWIPS;
    }

    @Override
    protected void applyPageSetup(XWPFDocument doc) {
        PerformanceWordBundlePageSetup.applyTo(doc);
    }

    @Override
    protected void registerHeadingStyles(XWPFDocument doc) {
        PerformanceWordStyleRegistrar.registerHeadingStyles(doc);
    }

    /**
     * 将 BufferedImage 编码为 JPEG 0.85 质量字节数组。
     * <p>300 DPI 保证高清分辨率，JPEG 0.85 保证视觉质量，
     * 同时内存占用仅为 PNG 的 1/5~1/10，避免大批量导出 OOM。
     * <p>含 alpha 通道的图片（TYPE_INT_ARGB 等）直接编码 JPEG 会抛
     * {@code IIOException: Bogus input colorspace}，先转为白底 RGB 再编码。
     */
    @Override
    protected EncodedImage encodeImage(BufferedImage img) throws IOException {
        BufferedImage rgbImg = toRgb(img);
        ByteArrayOutputStream imgOut = new ByteArrayOutputStream();
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(imgOut)) {
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(PerformanceWordStyleConfig.JPEG_COMPRESSION_QUALITY);
            writer.setOutput(ios);
            writer.write(null, new IIOImage(rgbImg, null, null), param);
        } finally {
            writer.dispose();
        }
        return new EncodedImage(
                imgOut.toByteArray(),
                XWPFDocument.PICTURE_TYPE_JPEG,
                "image.jpg"
        );
    }

    /**
     * 非 RGB 图片转为 TYPE_INT_RGB（白底填充），保证 JPEG 编码兼容。
     */
    private static BufferedImage toRgb(BufferedImage img) {
        if (img.getType() == BufferedImage.TYPE_INT_RGB) {
            return img;
        }
        BufferedImage rgb = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
            g.drawImage(img, 0, 0, null);
        } finally {
            g.dispose();
        }
        return rgb;
    }

    // ========== H1 客户类型 ==========

    private void writeCustomerTypeHeading(XWPFDocument doc, CustomerType customerType) {
        writeHeading(doc, customerType.displayName(), "Heading1");
    }

    // ========== H2 集团名称 ==========

    private void writeGroupCompanyHeading(XWPFDocument doc, String groupCompany) {
        writeHeading(doc, groupCompany, "Heading2");
    }

    // ========== H3 合同段落 + H3/H4 附件分类 ==========

    private void writeContractSection(XWPFDocument doc, ContractGroup contract) {
        // H3 合同名称
        writeHeading(doc, contract.contractName(), "Heading3");

        if (contract.attachmentTypes().isEmpty()) {
            writeBodyText(doc, LABEL_NO_ATTACHMENT);
            return;
        }

        for (AttachmentTypeGroup attType : contract.attachmentTypes()) {
            // 合同协议（CONTRACT_AGREEMENT）不输出标题，直接在合同名称下渲染附件图片
            // 参考文件：合同名称作为 H3，其下直接粘贴合同附件图，无"合同协议"标题
            String fileType = attType.fileType();
            if (PerformanceAttachmentTypeLabels.H4_ATTACHMENT_TYPE.equals(fileType)) {
                // BID_NOTICE 作为 H4
                writeHeading(doc, attType.label(), "Heading4");
            } else if (!PerformanceAttachmentTypeLabels.TYPE_CONTRACT_AGREEMENT.equals(fileType)) {
                // 其他附件类型（非合同协议、非中标通知书）作为 H3
                writeHeading(doc, attType.label(), "Heading3");
            }
            writeAttachments(doc, attType.attachments());
        }
    }

    // ========== 附件内容（PDF 高清渲染 / 图片直接嵌入） ==========

    private void writeAttachments(XWPFDocument doc, List<PerformanceDTO.AttachmentDTO> attachments) {
        for (PerformanceDTO.AttachmentDTO att : attachments) {
            Path file = resolveAttachmentPath(att.fileUrl());
            if (file == null || !Files.exists(file)) {
                log.warn("附件文件不存在: fileUrl={}, resolvedPath={}",
                        att.fileUrl(),
                        file != null ? file.toAbsolutePath() : "null");
                writeBodyText(doc, LABEL_FILE_MISSING);
                continue;
            }
            renderAttachment(doc, file, att.fileName());
        }
    }

    /**
     * 解析附件 fileUrl 到本地路径。
     * 委托 {@link AttachmentPathResolver#resolveLocalPath} 处理两种格式：
     * 绝对路径（页面上传）与相对路径（批量导入）。
     */
    private Path resolveAttachmentPath(String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank()) return null;
        try {
            return attachmentPathResolver.resolveLocalPath(fileUrl);
        } catch (RuntimeException e) {
            log.warn("附件路径解析失败: fileUrl={}, error={}", fileUrl, e.getMessage());
            return null;
        }
    }
}
