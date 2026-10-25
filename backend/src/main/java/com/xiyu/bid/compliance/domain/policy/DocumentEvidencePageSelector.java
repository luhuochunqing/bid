// Input: 文档物理分页文本
// Output: 质检关键图像页集合，含扫描邻页，避免整本渲染耗尽预算
// Pos: compliance/domain/policy/ - 质检图像抽取选页（纯核心）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.compliance.domain.policy;

import com.xiyu.bid.compliance.domain.DocumentContent;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class DocumentEvidencePageSelector {
    private static final List<String> CUES = List.of("承诺函", "承诺书", "特此承诺", "投标函", "授权委托书",
            "身份证明", "身份证复印件", "投标保证金", "保证金回单", "银行回单", "电子保函", "盖章", "签章");
    private static final List<String> SEAL_CUES = List.of("承诺函", "承诺书", "特此承诺", "投标函",
            "授权委托书", "身份证明", "盖章", "签章", "单位章", "电子印章");
    private DocumentEvidencePageSelector() { }

    public static Set<Integer> select(final DocumentContent document) {
        Set<Integer> pages = new LinkedHashSet<>(select(document, CUES));
        pages.addAll(sealTargets(document));
        return Set.copyOf(pages);
    }

    public static Set<Integer> sealTargets(final DocumentContent document) {
        return select(document, SEAL_CUES);
    }

    private static Set<Integer> select(final DocumentContent document, final List<String> cues) {
        if (document == null) return Set.of();
        Set<Integer> result = new LinkedHashSet<>();
        for (DocumentContent.PageContent page : document.pages()) {
            if (BidLetterCheckPolicy.isCatalogOrOutlinePage(page.text())
                    || cues.stream().noneMatch(page.text()::contains)) continue;
            result.add(page.pageNumber());
            document.pages().stream()
                    .filter(neighbor -> Math.abs(neighbor.pageNumber() - page.pageNumber()) <= 2)
                    .filter(neighbor -> neighbor.text().replaceAll("\\s+", "").length() < 40)
                    .map(DocumentContent.PageContent::pageNumber).forEach(result::add);
        }
        return Set.copyOf(result);
    }
}
