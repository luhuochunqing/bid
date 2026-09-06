// Input: 投标文件分页内容 (DocumentContent) + 可选 CA 证书要求契约 (CaCertificateRequirement)
// Output: 电子标 CA 证书核查结果 (QualityCheckItemResult: PASS / FAIL / MANUAL_REVIEW)
// Pos: compliance/domain/policy/ - 电子标 CA 证书与签章核查策略（纯核心 FP-Java）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.compliance.domain.policy;

import com.xiyu.bid.compliance.domain.BidQualityCheckItem;
import com.xiyu.bid.compliance.domain.DocumentContent;
import com.xiyu.bid.compliance.domain.QualityCheckItemResult;
import com.xiyu.bid.compliance.domain.QualityEvidence;

import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 标书电子标 CA 数字证书 / 电子签章核查策略 (FP-Java 纯核心).
 */
public final class CaCertificateCheckPolicy {

    private static final String[] CA_HEADERS = {
            "CA数字证书", "数字证书认证", "电子签章", "电子印章", "CA证书", "数字证书", "CA电子签章", "CA认证"
    };

    private static final String[] FACT_ELEMENTS = {
            "CFCA", "沃通", "契约锁", "北京CA", "天威诚信", "GDCA", "SHECA", "上海CA", "山东CA", "广东CA", "浙江CA",
            "证书序列号", "序列号", "有效期至", "有效期限", "有效日期", "算法", "颁发者", "颁发机构", "颁发给", "电子锁", "UKEY", "签名人", "签名算法", "公钥"
    };

    private static final Pattern UNFILLED_PLACEHOLDER_PATTERN = Pattern.compile(
            "【此处(?:填写|粘贴|附|插入).*?(?:CA|数字证书|电子签章|电子印章).*?】|"
                    + "（此处(?:填写|粘贴|附|插入).*?(?:CA|数字证书|电子签章|电子印章).*?）|"
                    + "\\[此处(?:填写|粘贴|附|插入).*?(?:CA|数字证书|电子签章|电子印章).*?\\]|"
                    + "CA证书(?:附后|见下页|另附)|"
                    + "(?:CA数字证书|电子签章|CA证书)[^\\n\\r]{0,20}[_—]{4,}"
    );

    private CaCertificateCheckPolicy() {
    }

    // REUSE-OK: * — 质检电子标 CA 证书核查入口，业务域独立
    public static QualityCheckItemResult check(final DocumentContent bidDoc) {
        return check(bidDoc, null);
    }

    // REUSE-OK: * — 质检电子标 CA 证书带契约核查入口，业务域独立
    public static QualityCheckItemResult check(final DocumentContent bidDoc, final CaCertificateRequirement requirement) {
        BidQualityCheckItem item = BidQualityCheckItem.CA_CERTIFICATE;

        // 1. 招标文件明确免收 / 纸质标：直通 PASS
        if (requirement != null && !requirement.required()) {
            return QualityCheckItemResult.pass(
                    item,
                    "招标文件未要求 CA 电子签章或数字证书（纸质标/免收不涉及）",
                    Collections.emptyList());
        }

        if (bidDoc == null || bidDoc.pages().isEmpty()) {
            return QualityCheckItemResult.fail(
                    item,
                    "投标文件为空，未检测到 CA 数字证书或电子签章",
                    "请上传包含完整电子标有效性证明的投标文件",
                    Collections.emptyList());
        }

        // 2. 占位模板未填拦截
        DocumentContent.PageContent placeholderPage = findPlaceholderPage(bidDoc);
        if (placeholderPage != null) {
            List<QualityEvidence> evidences = List.of(QualityEvidence.bid(
                    "CA证书占位页", placeholderPage.pageNumber(),
                    BasicInfoCheckPolicy.snippet(placeholderPage.text(), 200)));
            return QualityCheckItemResult.fail(
                    item,
                    "检测到 CA 数字证书/电子签章存在未填写的占位模板或空白下划线，未实质提供有效证书凭证！",
                    "请上传正式有效的 CA 机构数字证书截图或完成交易平台电子签名",
                    evidences);
        }

        // 3. 真实有效证书凭证要素校验
        DocumentContent.PageContent certPage = findValidCertPage(bidDoc);
        if (certPage != null) {
            List<QualityEvidence> evidences = List.of(QualityEvidence.bid(
                    "CA证书有效凭证页", certPage.pageNumber(),
                    BasicInfoCheckPolicy.snippet(certPage.text(), 250)));
            return QualityCheckItemResult.pass(
                    item,
                    "包含真实有效的 CA 数字证书或电子签章有效性记录，要素齐全",
                    evidences);
        }

        // 4. 仅在目录中提及
        boolean mentionedInCatalog = bidDoc.pages().stream()
                .anyMatch(p -> BidLetterCheckPolicy.isCatalogOrOutlinePage(p.text()) && containsAny(p.text(), CA_HEADERS));
        if (mentionedInCatalog) {
            return QualityCheckItemResult.fail(
                    item,
                    "仅在目录中检索到 CA 数字证书条目，正文中未检测到实际证书复印件或有效性证明！",
                    "请在投标文件中补充正式的 CA 数字证书有效性证明正文",
                    Collections.emptyList());
        }

        // 5. 存疑场景
        return QualityCheckItemResult.manualReview(
                item,
                "未识别到 CA 证书明文字符或有效性截图（如为电子标，请在交易平台客户端核验 CA 锁签名有效性）",
                "建议使用 CA 客户端校验数字证书有效性",
                Collections.emptyList());
    }

    private static DocumentContent.PageContent findValidCertPage(final DocumentContent bidDoc) {
        for (DocumentContent.PageContent page : bidDoc.pages()) {
            String text = page.text();
            if (BidLetterCheckPolicy.isCatalogOrOutlinePage(text)) {
                continue;
            }
            if (UNFILLED_PLACEHOLDER_PATTERN.matcher(text).find()) {
                continue;
            }
            if (!containsAny(text, CA_HEADERS)) {
                continue;
            }
            // 真实证书要素校验：至少命中 1 个事实要素（机构、序列号、有效期等）
            int factHits = 0;
            for (String fact : FACT_ELEMENTS) {
                if (text.contains(fact)) {
                    factHits++;
                }
            }
            if (factHits >= 1) {
                return page;
            }
        }
        return null;
    }

    private static DocumentContent.PageContent findPlaceholderPage(final DocumentContent bidDoc) {
        for (DocumentContent.PageContent page : bidDoc.pages()) {
            String text = page.text();
            if (BidLetterCheckPolicy.isCatalogOrOutlinePage(text)) {
                continue;
            }
            if (containsAny(text, CA_HEADERS) && UNFILLED_PLACEHOLDER_PATTERN.matcher(text).find()) {
                return page;
            }
        }
        return null;
    }

    private static boolean containsAny(final String text, final String... keywords) {
        if (text == null) return false;
        for (String kw : keywords) {
            if (text.contains(kw)) return true;
        }
        return false;
    }
}
