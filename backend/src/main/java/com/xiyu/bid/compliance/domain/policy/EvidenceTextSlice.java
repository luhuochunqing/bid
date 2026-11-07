// Input: 已解析文档的分页或无分页全文
// Output: 保留输入页号或明确无定位的文本切片
// Pos: compliance/domain/policy/ - 评分与资格核验共用的纯值来源边界
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.compliance.domain.policy;

import com.xiyu.bid.compliance.domain.DocumentContent;
import java.util.List;

/** 分析范围与证据页号一起传递；全文兜底不生成虚假页号。 */
record EvidenceTextSlice(Integer pageNumber, String text) {
    /** 全文不是一张页面，不能因其中包含目录而剔除全部正文。 */
    boolean isCatalogPage() {
        return pageNumber != null && BidLetterCheckPolicy.isCatalogOrOutlinePage(text);
    }

    static List<EvidenceTextSlice> from(DocumentContent document) {
        if (document == null) return List.of();
        if (!document.pages().isEmpty()) {
            return document.pages().stream()
                    .map(p -> new EvidenceTextSlice(p.pageNumber(), p.text())).toList();
        }
        return document.fullText().isBlank() ? List.of()
                : List.of(new EvidenceTextSlice(null, document.fullText()));
    }
}
