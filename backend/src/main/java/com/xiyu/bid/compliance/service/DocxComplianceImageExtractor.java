// Input: 本地 DOCX 文件路径 + 已分页 DocumentContent + 候选页集合
// Output: 绑定了逻辑页码 pageHint 与合规章节标识的 PageImage 列表
// Pos: compliance/service/ - 大体积 DOCX 合规关键页图片按需流式抽取器
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.compliance.service;

import com.xiyu.bid.compliance.domain.DocumentContent;
import com.xiyu.bid.compliance.domain.PageImage;
import com.xiyu.bid.compliance.domain.policy.BidLetterCheckPolicy;
import lombok.extern.slf4j.Slf4j;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 针对 DOCX 投标文件的按需流式关键页图片抽取器.
 *
 * <p>解决两类问题：
 * 1. 大体积 DOCX（如 600MB+ 标书）因媒体总大小超过 Sidecar 64MB 内存保护阈值被跳过图片抽取；
 * 2. DOCX 流式提取的图片无物理页码（pageHint=null），导致后续 OCR 转写与签章视觉通道无法定位页面。
 * 通过 ZipFile + StAX 常数级内存流式扫描段落与图片引用，将合规章节图片绑定到 DocumentContent 对应页码。
 */
@Slf4j
public final class DocxComplianceImageExtractor {

    private static final int MAX_IMAGES = 12;
    private static final int MAX_IMAGES_PER_ANCHOR = 3;
    private static final int MAX_PARAGRAPH_TTL = 6;
    private static final long MAX_SINGLE_IMAGE_BYTES = 10L * 1024 * 1024;
    private static final long MAX_TOTAL_IMAGE_BYTES = 18L * 1024 * 1024;

    private static final List<String> COMPLIANCE_ANCHORS = List.of(
            "投标人承诺函", "投标承诺函", "公平交易承诺函", "初步评审承诺函",
            "投标函", "报价函", "响应函", "授权委托书", "法定代表人身份证明",
            "身份证明", "身份证", "营业执照", "许可证", "资质证书",
            "投标保证金", "保证金回单", "银行回单", "电子保函", "承诺函", "承诺书");

    private static final Pattern TOC_DOTS_PATTERN = Pattern.compile("[.·…]{3,}\\s*\\d+\\s*$");

    private DocxComplianceImageExtractor() {
    }

    public static List<PageImage> extract(
            final Path docxPath, final DocumentContent content, final Set<Integer> selectedPages) {
        if (docxPath == null || content == null || content.pages().isEmpty() || !Files.isRegularFile(docxPath)) {
            return List.of();
        }
        try (ZipFile zipFile = new ZipFile(docxPath.toFile())) {
            Map<String, String> relToTarget = parseRelationships(zipFile);
            if (relToTarget.isEmpty()) return List.of();
            List<ImageCandidate> candidates = scanDocumentParagraphs(zipFile, relToTarget);
            if (candidates.isEmpty()) return List.of();
            return materializeImages(zipFile, candidates, content, selectedPages == null ? Set.of() : selectedPages);
        } catch (IOException | XMLStreamException | RuntimeException e) {
            log.warn("DOCX 关键合规页流式抽图降级跳过: file={}, reason={}", docxPath.getFileName(), e.getMessage());
            return List.of();
        }
    }

    private static Map<String, String> parseRelationships(final ZipFile zipFile)
            throws IOException, XMLStreamException {
        ZipEntry relsEntry = zipFile.getEntry("word/_rels/document.xml.rels");
        if (relsEntry == null) return Map.of();
        Map<String, String> map = new HashMap<>();
        XMLInputFactory factory = newXmlInputFactory();
        try (InputStream in = zipFile.getInputStream(relsEntry)) {
            XMLStreamReader reader = factory.createXMLStreamReader(in);
            while (reader.hasNext()) {
                if (reader.next() == XMLStreamConstants.START_ELEMENT
                        && "Relationship".equals(reader.getLocalName())) {
                    String id = reader.getAttributeValue(null, "Id");
                    String entryPath = normalizeMediaTarget(reader.getAttributeValue(null, "Target"));
                    if (id != null && entryPath != null) map.put(id, entryPath);
                }
            }
        }
        return map;
    }

    private static List<ImageCandidate> scanDocumentParagraphs(
            final ZipFile zipFile, final Map<String, String> relToTarget) throws IOException, XMLStreamException {
        ZipEntry docEntry = zipFile.getEntry("word/document.xml");
        if (docEntry == null) {
            return List.of();
        }
        List<ImageCandidate> candidates = new ArrayList<>();
        Set<String> seenTargets = new LinkedHashSet<>();
        XMLInputFactory factory = newXmlInputFactory();

        String activeAnchorText = null;
        String activeKeyword = null;
        int remainingTtl = 0;
        int imagesForCurrentAnchor = 0;

        try (InputStream in = zipFile.getInputStream(docEntry)) {
            XMLStreamReader reader = factory.createXMLStreamReader(in);
            StringBuilder pText = null;
            List<String> pRids = null;

            while (reader.hasNext() && candidates.size() < MAX_IMAGES) {
                int event = reader.next();
                if (event == XMLStreamConstants.START_ELEMENT) {
                    String local = reader.getLocalName();
                    if ("p".equals(local)) {
                        pText = new StringBuilder();
                        pRids = new ArrayList<>(2);
                    } else if ("t".equals(local) && pText != null) {
                        pText.append(reader.getElementText());
                    } else if (pRids != null && ("blip".equals(local) || "imagedata".equals(local))) {
                        String rid = findRelAttribute(reader);
                        if (rid != null && relToTarget.containsKey(rid)) {
                            pRids.add(rid);
                        }
                    }
                } else if (event == XMLStreamConstants.END_ELEMENT && "p".equals(reader.getLocalName()) && pText != null) {
                    String text = pText.toString().trim();
                    String matchedAnchor = matchComplianceAnchor(text);
                    if (matchedAnchor != null) {
                        activeAnchorText = text.length() > 80 ? text.substring(0, 80) : text;
                        activeKeyword = matchedAnchor;
                        remainingTtl = MAX_PARAGRAPH_TTL;
                        imagesForCurrentAnchor = 0;
                    } else if (!text.isEmpty() && remainingTtl > 0) {
                        remainingTtl--;
                    }
                    if (remainingTtl > 0 && activeKeyword != null && pRids != null && !pRids.isEmpty()) {
                        for (String rid : pRids) {
                            if (imagesForCurrentAnchor >= MAX_IMAGES_PER_ANCHOR || candidates.size() >= MAX_IMAGES) {
                                break;
                            }
                            String target = relToTarget.get(rid);
                            if (target != null && seenTargets.add(target)) {
                                candidates.add(new ImageCandidate(target, activeKeyword, activeAnchorText));
                                imagesForCurrentAnchor++;
                            }
                        }
                    }
                    pText = null;
                    pRids = null;
                }
            }
        }
        return candidates;
    }

    private static List<PageImage> materializeImages(
            final ZipFile zipFile, final List<ImageCandidate> candidates,
            final DocumentContent content, final Set<Integer> selectedPages) throws IOException {
        List<PageImage> result = new ArrayList<>();
        long totalBytes = 0L;
        int index = 0;
        for (ImageCandidate candidate : candidates) {
            ZipEntry entry = zipFile.getEntry(candidate.entryPath());
            if (entry == null || entry.getSize() > MAX_SINGLE_IMAGE_BYTES) continue;
            byte[] bytes;
            try (InputStream in = zipFile.getInputStream(entry)) {
                bytes = in.readNBytes((int) MAX_SINGLE_IMAGE_BYTES + 1);
            }
            if (bytes.length == 0 || bytes.length > MAX_SINGLE_IMAGE_BYTES
                    || totalBytes + bytes.length > MAX_TOTAL_IMAGE_BYTES) continue;
            totalBytes += bytes.length;
            int pageHint = resolvePageNumber(content, selectedPages, candidate.anchorText(), candidate.keyword());
            String mimeType = detectMimeType(candidate.entryPath());
            result.add(new PageImage(index++, mimeType, candidate.keyword(), pageHint, 0, 0,
                    Base64.getEncoder().encodeToString(bytes)));
        }
        return List.copyOf(result);
    }

    private static int resolvePageNumber(
            final DocumentContent content, final Set<Integer> selectedPages,
            final String anchorText, final String keyword) {
        String snippet = anchorText != null && anchorText.length() > 24 ? anchorText.substring(0, 24) : anchorText;
        if (snippet != null && !snippet.isBlank()) {
            for (DocumentContent.PageContent page : content.pages()) {
                if (selectedPages.contains(page.pageNumber()) && hasNonTocLineContaining(page.text(), snippet)) {
                    return page.pageNumber();
                }
            }
            for (DocumentContent.PageContent page : content.pages()) {
                if (hasNonTocLineContaining(page.text(), snippet)) return page.pageNumber();
            }
        }
        for (DocumentContent.PageContent page : content.pages()) {
            if (selectedPages.contains(page.pageNumber()) && hasNonTocLineContaining(page.text(), keyword)) {
                return page.pageNumber();
            }
        }
        for (DocumentContent.PageContent page : content.pages()) {
            if (hasNonTocLineContaining(page.text(), keyword)) return page.pageNumber();
        }
        return selectedPages.stream().findFirst().orElse(content.pages().getFirst().pageNumber());
    }

    private static boolean hasNonTocLineContaining(final String pageText, final String needle) {
        if (pageText == null || needle == null || needle.isBlank()
                || BidLetterCheckPolicy.isCatalogOrOutlinePage(pageText) || !pageText.contains(needle)) {
            return false;
        }
        for (String rawLine : pageText.split("\\r?\\n")) {
            String line = rawLine.trim();
            if (line.contains(needle) && !TOC_DOTS_PATTERN.matcher(line).find() && !line.matches(".*\\d+\\s*$")) {
                return true;
            }
        }
        return false;
    }

    private static String matchComplianceAnchor(final String text) {
        if (text == null || text.isBlank() || text.length() > 160) {
            return null;
        }
        if ("目录".equals(text) || "目 录".equals(text)
                || TOC_DOTS_PATTERN.matcher(text).find() || text.matches(".*\\d+\\s*$")) {
            return null;
        }
        for (String anchor : COMPLIANCE_ANCHORS) {
            if (text.contains(anchor)) {
                return anchor;
            }
        }
        return null;
    }

    private static String findRelAttribute(final XMLStreamReader reader) {
        int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            String local = reader.getAttributeLocalName(i);
            if ("embed".equals(local) || "id".equals(local)) {
                return reader.getAttributeValue(i);
            }
        }
        return null;
    }

    private static String normalizeMediaTarget(final String target) {
        if (target == null || target.isBlank()) {
            return null;
        }
        String clean = target.startsWith("/") ? target.substring(1) : target;
        if (!clean.startsWith("word/")) {
            clean = "word/" + clean;
        }
        String lower = clean.toLowerCase(Locale.ROOT);
        if (!lower.contains("media/") || !(lower.endsWith(".png") || lower.endsWith(".jpg")
                || lower.endsWith(".jpeg") || lower.endsWith(".webp") || lower.endsWith(".bmp")
                || lower.endsWith(".gif"))) {
            return null;
        }
        return clean;
    }

    private static String detectMimeType(final String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".bmp")) return "image/bmp";
        return "image/jpeg";
    }

    private static XMLInputFactory newXmlInputFactory() {
        XMLInputFactory factory = XMLInputFactory.newInstance();
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);
        return factory;
    }

    private record ImageCandidate(String entryPath, String keyword, String anchorText) {
    }
}

