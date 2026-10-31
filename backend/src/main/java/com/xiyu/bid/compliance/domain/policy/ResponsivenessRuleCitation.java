// Input: 当前招投标原文及分页
// Output: 排除有来源的独立禁止规则后，保留实际负偏离或返回待确认
// Pos: compliance/domain/policy/ - 响应性原文引用范围核对
package com.xiyu.bid.compliance.domain.policy;

import com.xiyu.bid.compliance.domain.BidQualityCheckItem;
import com.xiyu.bid.compliance.domain.DocumentContent;
import com.xiyu.bid.compliance.domain.QualityCheckItemResult;
import com.xiyu.bid.compliance.domain.QualityCheckStatus;
import com.xiyu.bid.compliance.domain.QualityEvidence;
import java.util.ArrayList;
import java.util.regex.Pattern;

final class ResponsivenessRuleCitation {
    private static final Pattern SENTENCE = Pattern.compile("[^。；;\\n]+[。；;]?");
    private static final Pattern PROHIBITION = Pattern.compile("(?:不允许|不得|禁止)[^。；;]{0,12}负偏[离差]");

    private ResponsivenessRuleCitation() { }

    static QualityCheckItemResult check(DocumentContent bid, DocumentContent tender) {
        if (bid == null || tender == null || tender.fullText().isBlank())
            return ResponsivenessReviewPolicy.check(bid);
        var pages = new ArrayList<DocumentContent.PageContent>();
        var evidence = new ArrayList<QualityEvidence>();
        String source = tender.fullText();
        for (var page : bid.pages()) {
            StringBuilder masked = new StringBuilder(page.text());
            var matcher = SENTENCE.matcher(page.text());
            while (matcher.find()) {
                String sentence = matcher.group().trim();
                if ((!PROHIBITION.matcher(sentence).find() && !sentence.contains("负偏差是指"))
                        || !source.contains(sentence)) continue;
                // 仅独立规则原句，投标人主体和表格单元格不能据此被豁免。
                if (sentence.matches("(?s).*(?:我方|我司|本公司|本单位|本投标人|\\||\\t).*")) continue;
                for (int i = matcher.start(); i < matcher.end(); i++) masked.setCharAt(i, ' ');
                evidence.add(QualityEvidence.bid("招标规则引用", page.pageNumber(), sentence));
                Integer sourcePage = null;
                for (var tp : tender.pages()) if (tp.text().contains(sentence)) { sourcePage = tp.pageNumber(); break; }
                evidence.add(QualityEvidence.tender("招标原文规则", sourcePage, sentence));
            }
            pages.add(new DocumentContent.PageContent(page.pageNumber(), masked.toString(), page.headings()));
        }
        if (evidence.isEmpty()) return ResponsivenessReviewPolicy.check(bid);
        var filtered = new DocumentContent(bid.fileName(), bid.fileHash(),
                pages.stream().map(DocumentContent.PageContent::text).collect(java.util.stream.Collectors.joining("\n")), pages);
        var result = ResponsivenessReviewPolicy.check(filtered);
        if (result != null && result.status() == QualityCheckStatus.FAIL
                && (!result.conclusion().contains("仅在目录") || !hasResponseRow(bid))) {
            var restored = new ArrayList<QualityEvidence>();
            for (var e : result.evidences()) {
                QualityEvidence original = e;
                if ("BID".equals(e.sourceType()) && !e.quote().isEmpty()) {
                    for (int i = 0; i < pages.size(); i++) {
                        if (!Integer.valueOf(pages.get(i).pageNumber()).equals(e.pageNumber())) continue;
                        int start = pages.get(i).text().indexOf(e.quote());
                        if (start >= 0) original = QualityEvidence.bid(e.sourceTitle(), e.pageNumber(),
                                bid.pages().get(i).text().substring(start, start + e.quote().length()));
                    }
                }
                restored.add(original);
            }
            return new QualityCheckItemResult(result.itemCode(), result.category(), result.title(),
                    result.description(), result.status(), result.severity(), result.order(),
                    result.conclusion(), result.suggestion(), restored, result.details());
        }
        // 原始页摘录保留投标声明供核对，不能以声明证明满足全部要求。
        for (var page : bid.pages()) {
            if (page.text().contains("完全响应")) {
                int index = page.text().indexOf("完全响应");
                evidence.add(QualityEvidence.bid("投标响应待核实", page.pageNumber(),
                        BasicInfoCheckPolicy.snippetAt(page.text(), index, index + 4, 200)));
                break;
            }
        }
        return QualityCheckItemResult.manualReview(BidQualityCheckItem.RESPONSIVENESS_REVIEW,
                "已排除招标规则引用，实际响应仍需核实", "请对照招标要求确认实际响应", evidence);
    }

    private static String tableLine(String raw) {
        String line = raw.trim();
        if (!line.contains("|") && line.contains("\t") && line.split("\t+").length >= 2)
            return "| " + String.join(" | ", line.split("\t+")) + " |";
        return line;
    }

    private static boolean hasResponseRow(DocumentContent bid) {
        for (var page : bid.pages()) for (String line : page.text().split("\\R")) {
            if (line.contains("|") && line.matches(".*(?:完全响应|全部响应).*")
                    && line.matches(".*(?:无偏差|无偏离).*")) return true;
        }
        return false;
    }

    static QualityEvidence negativeRowEvidence(DocumentContent bid,
            DeviationTableExtractor.ExtractionResult extraction, DeviationTableExtractor.DeviationRow row) {
        for (var table : extraction.tables()) {
            if (!table.rows().contains(row)) continue;
            for (var page : bid.pages()) {
                if (page.pageNumber() != table.pageNumber()) continue;
                for (String line : page.text().split("\\R")) {
                    // rawLine 可能已将制表符转换为管道；只返回原文中的实际行。
                    if (line.trim().equals(row.rawLine().trim())
                            || tableLine(line).equals(row.rawLine().trim()))
                        return QualityEvidence.bid("偏离表负偏离条款", page.pageNumber(), line);
                }
            }
        }
        return QualityEvidence.bid("负偏离行原文未能定位", null, "");
    }
}
