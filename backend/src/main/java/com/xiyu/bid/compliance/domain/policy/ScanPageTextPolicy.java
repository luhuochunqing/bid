// Input: 原文分页与页面图像、受限视觉转写结果
// Output: 仅补充候选扫描页的文档，不推断印章、证件真伪或材料完整性
// Pos: compliance/domain/policy/ - 扫描授权及身份证明文字补充（纯核心）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.compliance.domain.policy;

import com.xiyu.bid.compliance.domain.DocumentContent;
import com.xiyu.bid.compliance.domain.PageImage;
import java.util.List;

public final class ScanPageTextPolicy {
    public static final String TRANSCRIPTION_MARKER = "【页面图像转写，非验签】";
    private ScanPageTextPolicy() { }
    public record Transcription(int pageNumber, String text, double confidence) { }
    public static List<Integer> candidatePages(final DocumentContent document) {
        if (document == null) return List.of();
        var anchors = document.pages().stream()
                .filter(p -> !BidLetterCheckPolicy.isCatalogOrOutlinePage(p.text()))
                .filter(p -> p.text().contains("身份证明") || p.text().contains("授权委托书") || p.text().contains("身份证复印件"))
                .map(DocumentContent.PageContent::pageNumber).toList();
        var imaged = document.images().stream().map(PageImage::pageHint)
                .filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.toSet());
        return document.pages().stream()
                .filter(p -> p.text().replaceAll("\\s+", "").length() < 40)
                .filter(p -> imaged.contains(p.pageNumber()))
                .filter(p -> anchors.stream().anyMatch(n -> Math.abs(n - p.pageNumber()) <= 2))
                .map(DocumentContent.PageContent::pageNumber).distinct().sorted().limit(8).toList();
    }
    public static DocumentContent enrich(final DocumentContent document, final List<Transcription> texts) {
        if (document == null || texts == null || texts.isEmpty()) return document;
        var candidates = candidatePages(document);
        var accepted = texts.stream().filter(t -> t != null && candidates.contains(t.pageNumber()))
                .filter(t -> Double.isFinite(t.confidence()) && t.confidence() >= 0.90 && t.confidence() <= 1.0)
                .filter(t -> t.text() != null && !t.text().isBlank() && t.text().length() <= 6000)
                .collect(java.util.stream.Collectors.groupingBy(Transcription::pageNumber));
        var pages = document.pages().stream().map(page -> {
            var found = accepted.get(page.pageNumber());
            if (found == null || found.size() != 1) return page;
            return new DocumentContent.PageContent(page.pageNumber(), page.text() + "\n" + TRANSCRIPTION_MARKER + "\n"
                    + found.getFirst().text(), page.headings());
        }).toList();
        if (pages.equals(document.pages())) return document;
        return new DocumentContent(document.fileName(), document.fileHash(),
                pages.stream().map(DocumentContent.PageContent::text).collect(java.util.stream.Collectors.joining("\n")),
                pages, document.images());
    }
}
