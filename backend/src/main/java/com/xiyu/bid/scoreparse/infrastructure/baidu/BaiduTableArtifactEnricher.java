// Input: DocumentArtifact baseline, PDF bytes and file name
// Output: Baidu Table OCR enriched DocumentArtifact
// Pos: scoreparse/infrastructure/baidu - PDF Table OCR enricher
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.scoreparse.infrastructure.baidu;

import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact.ArtifactMeta;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact.CanonicalTable;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 百度智能云表格识别（Table OCR）文档增强器。
 * <p>当系统配置启用了百度 OCR 时，针对 PDF 招投标文件智能探测并调用高精度表格识别，
 * 解决底层 Sidecar 在跨页续表、大合并单元格处丢失表格或坐标断裂的缺陷。
 * <p>具备平滑降级（Graceful Degradation）容灾能力：百度 OCR 未启用或调用异常时直接回退 baseline。
 */
@Slf4j
@Component
public class BaiduTableArtifactEnricher {

    private static final List<String> SCORING_KEYWORDS = List.of(
            "评分", "评标", "评审标准", "评审因素", "分值", "详细评审", "综合评分", "得分", "价格优惠", "商务标", "技术标"
    );

    private static final int MAX_PAGES_TO_SCAN = 15;

    private final BaiduTableOcrClient baiduTableOcrClient;

    public BaiduTableArtifactEnricher(@Autowired(required = false) BaiduTableOcrClient baiduTableOcrClient) {
        this.baiduTableOcrClient = baiduTableOcrClient;
    }

    public boolean isEnabled() {
        return baiduTableOcrClient != null && baiduTableOcrClient.isEnabled();
    }

    /**
     * 尝试用百度表格 OCR 增强现有的 DocumentArtifact。
     *
     * @param baseline 原 Sidecar 提取的 DocumentArtifact（可为 null）
     * @param pdfBytes PDF 原始二进制数据
     * @param fileName 文件名
     * @return 增强后的 DocumentArtifact
     */
    public DocumentArtifact enrichIfEnabled(DocumentArtifact baseline, byte[] pdfBytes, String fileName) {
        if (!isEnabled()) {
            return baseline;
        }
        if (fileName == null || !fileName.toLowerCase().endsWith(".pdf") || pdfBytes == null || pdfBytes.length == 0) {
            return baseline;
        }

        try {
            List<Integer> candidatePages = detectScoringPages(pdfBytes);
            if (candidatePages.isEmpty()) {
                log.info("BaiduTableArtifactEnricher: No scoring pages detected in PDF: {}", fileName);
                return baseline;
            }

            log.info("BaiduTableArtifactEnricher: Detected scoring pages {} for {}", candidatePages, fileName);
            List<CanonicalTable> ocrTables = new ArrayList<>();
            List<String> warnings = new ArrayList<>();

            for (int pageNum : candidatePages) {
                try {
                    var response = baiduTableOcrClient.extractTablesFromPdf(pdfBytes, pageNum);
                    if (response != null && response.isSuccess() && !response.tablesResult().isEmpty()) {
                        String section = inferSectionForPage(baseline, pageNum);
                        List<CanonicalTable> tables = BaiduTableArtifactAdapter.toCanonicalTables(response, pageNum, section);
                        ocrTables.addAll(tables);
                        log.info("BaiduTableArtifactEnricher: Page {} recognized {} tables via Baidu OCR", pageNum, tables.size());
                    }
                } catch (IOException | RuntimeException e) {
                    log.warn("BaiduTableArtifactEnricher: Failed to recognize page {} via Baidu OCR: {}", pageNum, e.getMessage());
                    warnings.add("Baidu OCR page " + pageNum + " failed: " + e.getMessage());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("BaiduTableArtifactEnricher: Interrupted while recognizing page {} via Baidu OCR", pageNum);
                    warnings.add("Baidu OCR page " + pageNum + " interrupted");
                    break;
                }
            }

            if (ocrTables.isEmpty()) {
                return baseline;
            }

            return mergeArtifact(baseline, ocrTables, warnings, fileName);
        } catch (IOException | RuntimeException e) {
            log.error("BaiduTableArtifactEnricher: Unexpected error while enriching artifact for {}: {}", fileName, e.getMessage(), e);
            return baseline;
        }
    }

    /**
     * 利用 PDFBox 快速扫描 PDF 页面文本，筛选评分关键页及其续表页。
     */
    public List<Integer> detectScoringPages(byte[] pdfBytes) throws IOException {
        Set<Integer> targetPages = new LinkedHashSet<>();
        try (PDDocument doc = PDDocument.load(new ByteArrayInputStream(pdfBytes))) {
            int totalPages = doc.getNumberOfPages();
            if (totalPages <= 10) {
                for (int p = 1; p <= totalPages; p++) {
                    targetPages.add(p);
                }
                return List.copyOf(targetPages);
            }

            PDFTextStripper stripper = new PDFTextStripper();
            for (int p = 1; p <= totalPages; p++) {
                stripper.setStartPage(p);
                stripper.setEndPage(p);
                String text = stripper.getText(doc);
                if (containsScoringKeyword(text)) {
                    targetPages.add(p);
                    // 续表守护：若当前页包含评分关键词，其下一页极大可能是跨页续表，一并加入
                    if (p + 1 <= totalPages) {
                        targetPages.add(p + 1);
                    }
                }
                if (targetPages.size() >= MAX_PAGES_TO_SCAN) {
                    break;
                }
            }
        }
        return List.copyOf(targetPages);
    }

    private boolean containsScoringKeyword(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        for (String kw : SCORING_KEYWORDS) {
            if (text.contains(kw)) {
                return true;
            }
        }
        return false;
    }

    private String inferSectionForPage(DocumentArtifact baseline, int pageNum) {
        if (baseline != null && baseline.tables() != null) {
            for (CanonicalTable t : baseline.tables()) {
                if (t != null && t.sourcePage() != null && t.sourcePage() == pageNum) {
                    if (t.sourceSection() != null && !t.sourceSection().isBlank()) {
                        return t.sourceSection();
                    }
                }
            }
            // 邻近页推断
            for (CanonicalTable t : baseline.tables()) {
                if (t != null && t.sourcePage() != null && Math.abs(t.sourcePage() - pageNum) <= 1) {
                    if (t.sourceSection() != null && !t.sourceSection().isBlank()) {
                        return t.sourceSection();
                    }
                }
            }
        }
        return "第三章 评审办法/详细评审标准";
    }

    private DocumentArtifact mergeArtifact(DocumentArtifact baseline, List<CanonicalTable> ocrTables,
                                           List<String> extraWarnings, String fileName) {
        if (baseline == null) {
            ArtifactMeta meta = new ArtifactMeta("doc-" + System.currentTimeMillis(), "", "PDF", "baidu-ocr-fallback");
            return new DocumentArtifact(meta, ocrTables, extraWarnings);
        }

        Set<Integer> ocrPages = new LinkedHashSet<>();
        for (CanonicalTable t : ocrTables) {
            if (t.sourcePage() != null) {
                ocrPages.add(t.sourcePage());
            }
        }

        List<CanonicalTable> mergedTables = new ArrayList<>();
        // 保留 baseline 中未被百度 OCR 覆盖的页面表格
        if (baseline.tables() != null) {
            for (CanonicalTable bt : baseline.tables()) {
                if (bt.sourcePage() == null || !ocrPages.contains(bt.sourcePage())) {
                    mergedTables.add(bt);
                }
            }
        }
        // 加入百度 OCR 高保真提取的表格（含补漏和替换）
        mergedTables.addAll(ocrTables);

        List<String> mergedWarnings = new ArrayList<>(baseline.warnings() != null ? baseline.warnings() : List.of());
        mergedWarnings.addAll(extraWarnings);
        mergedWarnings.add("Enriched with Baidu Table OCR on pages " + ocrPages);

        return new DocumentArtifact(
                baseline.artifact(),
                mergedTables,
                mergedWarnings,
                baseline.textBlocks(),
                baseline.schemaVersion(),
                baseline.documents()
        );
    }
}
