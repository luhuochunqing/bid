// Input: 投标文件分页正文
// Output: 承诺书签署核查结果与正文页证据
// Pos: compliance/domain/policy/ - 承诺正文定位与文本签章边界
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.compliance.domain.policy;
import com.xiyu.bid.compliance.domain.BidQualityCheckItem;
import com.xiyu.bid.compliance.domain.DocumentContent;
import com.xiyu.bid.compliance.domain.QualityCheckItemResult;
import com.xiyu.bid.compliance.domain.QualityEvidence;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

public final class CommitmentSealCheckPolicy {
    private CommitmentSealCheckPolicy() { }
    private static final Pattern COMMITMENT_SEAL_MARK_PATTERN = Pattern.compile(
            "（公章）|\\(公章\\)|（盖章）|\\(盖章\\)|盖单位公章|加盖公章|公章|签字"
    );

    // REUSE-OK: backend/src/main/java/com/xiyu/bid/biddraftagent/domain/GapCheckPolicy.java — 该策略检查草稿快照缺口，本方法核查分页承诺书签章，输入与规则不同。
    public static QualityCheckItemResult check(final DocumentContent bidDoc) {
        BidQualityCheckItem item = BidQualityCheckItem.COMMITMENT_SEAL;
        BasicInfoCheckPolicy.PageMatch match = findCommitmentBody(bidDoc);
        if (match != null) {
            String text = match.text();
            boolean hasPlaceholder = SealIntegrityCheckPolicy.hasUnfilledSealPlaceholder(text);
            boolean hasPositiveEvidence = SealIntegrityCheckPolicy.hasPositiveSealEvidence(text);
            boolean hasSealMark = COMMITMENT_SEAL_MARK_PATTERN.matcher(text).find();
            if (hasPlaceholder && !hasPositiveEvidence) {
                return QualityCheckItemResult.fail(item, "承诺书包含未填写的空白占位或未盖章模板标记", "请在承诺书落款处加盖真实公章及法定代表人签字，不得保留下划线或模板占位符",
                        List.of(QualityEvidence.bid("承诺书占位留白", match.pageNumber(), BasicInfoCheckPolicy.snippetAround(text, 150, "承诺书", "承诺函", "____", "【"))));
            }
            if (hasPositiveEvidence) {
                return QualityCheckItemResult.pass(item, "包含合规承诺书且签章要素齐全", List.of(QualityEvidence.bid("承诺书签署", match.pageNumber(), BasicInfoCheckPolicy.snippetAround(text, 200, "承诺书", "承诺函", "公章", "盖章", "签字"))));
            }
            if (hasSealMark) {
                return QualityCheckItemResult.manualReview(item, "已检索到承诺书，但落款处仅有签章格式说明文字，文本层无法确认实际盖章", "请人工复核承诺书落款处是否已完成盖章与签署（落款「（盖章）」等说明文字不代表已实际盖章）", List.of(QualityEvidence.bid("承诺书章节", match.pageNumber(), BasicInfoCheckPolicy.snippetAround(text, 200, "承诺书", "承诺函"))));
            }
            return QualityCheckItemResult.manualReview(item, "已检索到承诺书，但落款处未识别到明确的公章或签字标记", "请人工复核承诺书落款处是否已完成盖章与签署", List.of(QualityEvidence.bid("承诺书章节", match.pageNumber(), BasicInfoCheckPolicy.snippetAround(text, 200, "承诺书", "承诺函"))));
        }
        return QualityCheckItemResult.manualReview(item, "未检索到独立的承诺函章节", "请核实招标文件是否要求单独提供廉洁/无行贿承诺书", Collections.emptyList());
    }

    private static BasicInfoCheckPolicy.PageMatch findCommitmentBody(final DocumentContent doc) {
        BasicInfoCheckPolicy.PageMatch fallback = null;
        for (DocumentContent.PageContent page : doc.pages()) {
            String text = page.text();
            if (!BasicInfoCheckPolicy.containsAnyKeyword(text, "承诺书", "承诺函", "廉洁承诺", "诚信承诺")) continue;
            boolean body = text.contains("特此承诺") || text.contains("本公司郑重")
                    || text.contains("承诺人：") || text.contains("承诺人:")
                    || ((text.contains("我司承诺") || text.contains("我方承诺"))
                        && (text.contains("投标人：") || text.contains("投标人:")));
            if (body) return new BasicInfoCheckPolicy.PageMatch(page.pageNumber(), text);
            if (!text.contains("评审索引") && !text.contains("评审标准") && !text.contains("评审要素")
                    && !text.contains("......") && !text.contains("……") && fallback == null) {
                fallback = new BasicInfoCheckPolicy.PageMatch(page.pageNumber(), text);
            }
        }
        return fallback;
    }
}
