package com.xiyu.bid.scoreparse.infrastructure.baidu;

import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact.ArtifactMeta;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact.CanonicalTable;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 百度表格 OCR 增强器与主干抽取服务。
 * <p>在启用百度表格 OCR 时，作为评分页面的第一权威主干（Primary Authority）：
 * 扫描整个评分区间页面，提取高保真 2D 网格结构表格，并替换原 Sidecar 提取的粗粒度或断裂表格。
 */
@Component
public class BaiduTableArtifactEnricher {

    private static final Logger log = LoggerFactory.getLogger(BaiduTableArtifactEnricher.class);

    private static final List<String> SCORING_KEYWORDS = List.of(
            "评分标准", "评分细则", "详细评审标准", "评审因素及分值", "综合评分表",
            "评标办法", "评审办法", "分值构成", "技术评分", "商务评分", "综合评标", "评标得分", "评审因素"
    );

    private static final Pattern SECTION_PATTERN = Pattern.compile(
            "(第[一二三四五六七八九十0-9]+章\\s*[^\\r\\n]+)"
    );

    private static final int MAX_PAGES_TO_SCAN = 50;

    private final BaiduTableOcrClient baiduTableOcrClient;

    public BaiduTableArtifactEnricher(BaiduTableOcrClient baiduTableOcrClient) {
        this.baiduTableOcrClient = baiduTableOcrClient;
    }

    public boolean isEnabled() {
        return baiduTableOcrClient != null && baiduTableOcrClient.isEnabled();
    }

    /**
     * 尝试用百度表格 OCR 增强或主管替换现有的 DocumentArtifact。
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
        if (fileName == null || !fileName.toLowerCase(java.util.Locale.ROOT).endsWith(".pdf") || pdfBytes == null || pdfBytes.length == 0) {
            return baseline;
        }

        try {
            List<Integer> candidatePages = detectScoringPages(pdfBytes);
            if (candidatePages.isEmpty()) {
                log.info("BaiduTableArtifactEnricher: No scoring pages detected in PDF: {}", fileName);
                return baseline;
            }

            List<Integer> pagesToScan = determinePagesToScan(baseline, candidatePages);
            if (pagesToScan.isEmpty()) {
                log.info("BaiduTableArtifactEnricher: No target scoring pages to scan for {}", fileName);
                return baseline;
            }

            log.info("BaiduTableArtifactEnricher: Scanning target scoring pages {} (candidates={}) for {}",
                    pagesToScan, candidatePages, fileName);
            List<CanonicalTable> ocrTables = new ArrayList<>();
            List<String> warnings = new ArrayList<>();

            for (int pageNum : pagesToScan) {
                try {
                    var response = baiduTableOcrClient.extractTablesFromPdf(pdfBytes, pageNum);
                    if (response != null && response.isSuccess() && !response.tablesResult().isEmpty()) {
                        String section = inferSectionForPage(baseline, pdfBytes, pageNum);
                        List<CanonicalTable> tables = BaiduTableArtifactAdapter.toCanonicalTables(response, pageNum, section);
                        ocrTables.addAll(tables);
                        log.info("BaiduTableArtifactEnricher: Page {} recognized {} tables via Baidu OCR", pageNum, tables.size());
                    } else if (response == null || !response.isSuccess()) {
                        warnings.add("BAIDU_OCR_PAGE_FAILED:" + pageNum + ":"
                                + (response == null ? "EMPTY_RESPONSE" : "PROVIDER_ERROR_" + response.errorCode()));
                    }
                } catch (IOException | RuntimeException e) {
                    log.warn("BaiduTableArtifactEnricher: Failed to recognize page {} via Baidu OCR: {}", pageNum, e.getMessage());
                    warnings.add("BAIDU_OCR_PAGE_FAILED:" + pageNum + ":" + failureCode(e));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("BaiduTableArtifactEnricher: Interrupted while recognizing page {} via Baidu OCR", pageNum);
                    warnings.add("BAIDU_OCR_PAGE_FAILED:" + pageNum + ":INTERRUPTED");
                    break;
                }
            }

            if (ocrTables.isEmpty()) {
                return warnings.isEmpty() ? baseline : withWarnings(baseline, warnings);
            }

            List<CanonicalTable> linkedOcrTables = BaiduCrossPageTableLinker.mergeCrossPageScoreTables(ocrTables,
                    BaiduRepeatedHeaderPolicy.originalPageOwners(baseline == null ? List.of() : baseline.tables()));
            return mergeArtifact(baseline, linkedOcrTables, warnings, fileName);
        } catch (IOException | RuntimeException e) {
            log.error("BaiduTableArtifactEnricher: Unexpected error while enriching artifact for {}: {}", fileName, e.getMessage(), e);
            return withWarnings(baseline, List.of("BAIDU_OCR_ENRICHMENT_FAILED:" + failureCode(e)));
        }
    }

    private static String failureCode(Exception failure) {
        // Provider exceptions may include URLs or request details; persist only stable safe codes.
        String message = String.valueOf(failure.getMessage());
        var status = java.util.regex.Pattern.compile("(?i)HTTP(?: error:)?\\s+(\\d{3})").matcher(message);
        return status.find() ? "HTTP_" + status.group(1) : failure.getClass().getSimpleName();
    }

    private static DocumentArtifact withWarnings(DocumentArtifact baseline, List<String> warnings) {
        if (baseline == null) return new DocumentArtifact(new ArtifactMeta("baidu-ocr-unavailable", "", "PDF", "baidu-ocr"),
                List.of(), warnings);
        return new DocumentArtifact(baseline.artifact(), baseline.tables(),
                java.util.stream.Stream.concat(baseline.warnings().stream(), warnings.stream()).distinct().toList(),
                baseline.textBlocks(), baseline.schemaVersion(), baseline.documents());
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
                    if (p + 1 <= totalPages) {
                        targetPages.add(p + 1);
                    }
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

    /**
     * 确定需要百度 OCR 扫描的页面列表。
     * <p>在 Primary Authority 模式下，覆盖整个评分区间的所有关键页，进行高精度主干抽取并替换原表。
     * <p>委托 {@link BaiduScanPageSelector} 基于连续簇聚类与评分表锚点重合识别，实现双向连通。
     */
    List<Integer> determinePagesToScan(DocumentArtifact baseline, List<Integer> candidatePages) {
        return BaiduScanPageSelector.determinePagesToScan(baseline, candidatePages, MAX_PAGES_TO_SCAN);
    }

    private String inferSectionForPage(DocumentArtifact baseline, byte[] pdfBytes, int pageNum) {
        if (baseline != null && baseline.tables() != null) {
            for (CanonicalTable t : baseline.tables()) {
                if (t != null && t.sourcePage() != null && t.sourcePage() == pageNum
                        && t.sourceSection() != null && !t.sourceSection().isBlank()) {
                    return t.sourceSection();
                }
            }
            for (int diff = 1; diff <= 3; diff++) {
                for (CanonicalTable t : baseline.tables()) {
                    if (t != null && t.sourcePage() != null && Math.abs(t.sourcePage() - pageNum) == diff
                            && t.sourceSection() != null && !t.sourceSection().isBlank()) {
                        return t.sourceSection();
                    }
                }
            }
        }
        String fromDoc = extractSectionHeading(pdfBytes, pageNum);
        if (fromDoc != null && !fromDoc.isBlank()) {
            return fromDoc;
        }
        return "评审办法/详细评审标准";
    }

    private String extractSectionHeading(byte[] pdfBytes, int pageNum) {
        if (pdfBytes == null || pdfBytes.length == 0) return null;
        try (PDDocument doc = PDDocument.load(new ByteArrayInputStream(pdfBytes))) {
            PDFTextStripper stripper = new PDFTextStripper();
            for (int p = pageNum; p >= Math.max(1, pageNum - 2); p--) {
                stripper.setStartPage(p);
                stripper.setEndPage(p);
                String text = stripper.getText(doc);
                var matcher = SECTION_PATTERN.matcher(text);
                if (matcher.find()) {
                    return matcher.group(1).trim();
                }
            }
        } catch (IOException ignored) {
            // PDFBox 文本提取异常降级
        }
        return null;
    }

    private DocumentArtifact mergeArtifact(DocumentArtifact baseline, List<CanonicalTable> ocrTables,
                                           List<String> extraWarnings, String fileName) {
        if (baseline == null) {
            ArtifactMeta meta = new ArtifactMeta("doc-" + System.currentTimeMillis(), "", "PDF", "baidu-ocr-fallback");
            return new DocumentArtifact(meta, ocrTables, extraWarnings);
        }

        Set<Integer> preservedPages = BaiduOcrReplacementGuard.preservedPages(baseline.tables(), ocrTables);
        List<CanonicalTable> safeOcrTables = BaiduOcrReplacementGuard.excludingPages(ocrTables, preservedPages);
        Set<Integer> ocrPages = new LinkedHashSet<>();
        for (CanonicalTable t : safeOcrTables) {
            if (t.sourcePage() != null) {
                ocrPages.add(t.sourcePage());
            }
            if (t.cells() != null) {
                for (var c : t.cells()) {
                    if (c.sourcePage() != null) {
                        ocrPages.add(c.sourcePage());
                    }
                }
            }
        }

        List<CanonicalTable> mergedTables = new ArrayList<>();
        if (baseline.tables() != null) {
            for (CanonicalTable bt : baseline.tables()) {
                if (bt.sourcePage() == null || !ocrPages.contains(bt.sourcePage())) {
                    mergedTables.add(bt);
                }
            }
        }
        mergedTables.addAll(safeOcrTables);

        List<String> mergedWarnings = new ArrayList<>(baseline.warnings() != null ? baseline.warnings() : List.of());
        mergedWarnings.addAll(extraWarnings);
        mergedWarnings.add("Enriched with Baidu Table OCR on pages " + ocrPages);
        if (!preservedPages.isEmpty()) mergedWarnings.add("Preserved PDF grid on OCR-collapsed pages " + preservedPages);
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
