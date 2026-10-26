// Input: 临时标书文件 + 文本 Sidecar 结果 + 页面图像端口
// Output: 带文本与页面图像的合规文档内容
// Pos: compliance/service/ - 合规文档 Sidecar 组合适配器
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.compliance.service;

import com.xiyu.bid.compliance.domain.DocumentContent;
import com.xiyu.bid.compliance.domain.PageImage;
import com.xiyu.bid.compliance.domain.policy.DocumentPageVisionDetector;
import com.xiyu.bid.compliance.domain.policy.ScanPageTextPolicy;
import org.springframework.beans.factory.annotation.Autowired;
import com.xiyu.bid.compliance.domain.PageImageExtractor;
import com.xiyu.bid.compliance.domain.SidecarParseOutcome;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import com.xiyu.bid.compliance.domain.policy.DocumentEvidencePageSelector;

/** 组合文本解析与页面图像抽取，避免 Loader 继续膨胀 collaborator 数量。 */
@Component
@Slf4j
public class SidecarComplianceDocumentAdapter {

    private final SidecarStreamDocumentAdapter textAdapter;
    private final PageImageExtractor imageExtractor;

    @Autowired(required = false)
    private DocumentPageVisionDetector documentPageVisionDetector;

    public SidecarComplianceDocumentAdapter(
            final SidecarStreamDocumentAdapter textAdapter,
            final PageImageExtractor imageExtractor) {
        this.textAdapter = textAdapter;
        this.imageExtractor = imageExtractor;
    }

    public SidecarParseOutcome convertDocument(final Path filePath, final String fileName) {
        return textAdapter.convertDocument(filePath, fileName);
    }

    /** 图像通道失败只降级视觉核验，不阻断文本质检。 */
    public DocumentContent withPageImages(
            final Path physicalFile, final String fileName, final DocumentContent content) {
        if (content == null || content.pages().isEmpty()) {
            return content;
        }
        try {
            Set<Integer> pages = DocumentEvidencePageSelector.select(content);
            if (pages.isEmpty()) return content;
            List<PageImage> images = imageExtractor.extract(physicalFile, fileName, pages);
            if (images.isEmpty()) {
                // 空图不是异常但必须可观测：签章视觉核验会因此降级人工复核（2026-09-21 项目 1382 假通过放大器）
                log.warn("投标文件未抽取到页面图像，签章视觉通道不可用：{}（请求页数={}）", fileName, pages.size());
            }
            DocumentContent withImages = new DocumentContent(content.fileName(), content.fileHash(), content.fullText(),
                    content.pages(), images);
            return documentPageVisionDetector == null ? withImages : ScanPageTextPolicy.enrich(
                    withImages, documentPageVisionDetector.transcribe(withImages));
        } catch (IOException | InterruptedException | RuntimeException e) {
            log.warn("投标文件页面图像抽取失败，签章视觉通道降级为不可用：{}，原因={}", fileName, e.getMessage());
            return content;
        }
    }
}
