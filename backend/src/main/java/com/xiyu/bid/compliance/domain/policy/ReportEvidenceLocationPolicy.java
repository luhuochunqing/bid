// Input: 内部核查结果、带原件页来源的投标/招标文档
// Output: 原件页/来源文件定位；无物理来源或引文不对应时页码为空，保留摘录
// Pos: compliance/domain/policy/ - 报告持久化前的纯核心定位投影，所有融合完成后只调用一次
package com.xiyu.bid.compliance.domain.policy;

import com.xiyu.bid.compliance.domain.DocumentContent;
import com.xiyu.bid.compliance.domain.QualificationDetail;
import com.xiyu.bid.compliance.domain.QualityCheckItemResult;
import com.xiyu.bid.compliance.domain.QualityEvidence;

public final class ReportEvidenceLocationPolicy {
    private ReportEvidenceLocationPolicy() { }

    public static QualityCheckItemResult project(QualityCheckItemResult item, DocumentContent bid, DocumentContent tender) {
        var evidences = item.evidences().stream().map(e -> {
            if (!isDocumentEvidence(e)) return e;
            DocumentContent source = "TENDER".equals(e.sourceType()) ? tender : bid;
            var page = find(source, e.pageNumber());
            // 图像摘录为视觉观察，不能用文本包含关系验证；页身份仍须经原件映射。
            boolean located = page != null && ("BID_IMAGE".equals(e.sourceType())
                    || (!e.quote().isBlank() && page.text().contains(e.quote())));
            return new QualityEvidence(e.sourceType(), e.sourceTitle(), located ? page.sourcePageNumber() : null,
                    e.quote(), e.sourceRecordId(), e.confidence(), e.knowledgeSource(), e.matched(),
                    located ? page.sourceFileName() : "", located ? page.sourceFileHash() : "");
        }).toList();
        var details = item.details().stream().map(d -> {
            var page = find(bid, d.bidPageNumber());
            boolean located = page != null && !d.bidQuote().isBlank() && page.text().contains(d.bidQuote());
            return new QualificationDetail(d.requirement(), d.matchedCertName(), d.status(),
                    located ? page.sourcePageNumber() : null, d.bidQuote(), d.remainingDays(), d.remark(), d.finding(),
                    located ? page.sourceFileName() : "");
        }).toList();
        return new QualityCheckItemResult(item.itemCode(), item.category(), item.title(), item.description(), item.status(),
                item.severity(), item.order(), item.conclusion(), item.suggestion(), evidences, details);
    }

    private static boolean isDocumentEvidence(QualityEvidence e) {
        return "BID".equals(e.sourceType()) || "BID_IMAGE".equals(e.sourceType()) || "TENDER".equals(e.sourceType());
    }

    private static DocumentContent.PageContent find(DocumentContent source, Integer index) {
        if (source == null || index == null) return null;
        var hits = source.pages().stream().filter(p -> p.pageNumber() == index).toList();
        return hits.size() == 1 ? hits.getFirst() : null;
    }
}
