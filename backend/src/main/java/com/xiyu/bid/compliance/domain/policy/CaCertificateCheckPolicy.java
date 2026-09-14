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
        return check(bidDoc, null, Collections.emptyList());
    }

    // REUSE-OK: * — 质检电子标 CA 证书带契约核查入口，业务域独立
    public static QualityCheckItemResult check(final DocumentContent bidDoc, final CaCertificateRequirement requirement) {
        return check(bidDoc, requirement, Collections.emptyList());
    }

    // REUSE-OK: * — 质检电子标 CA 证书带契约与知识库资源核查入口，业务域独立
    public static QualityCheckItemResult check(
            final DocumentContent bidDoc,
            final CaCertificateRequirement requirement,
            final List<QualityEvidence> kbEvidences) {
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

        // 3. CA 资源库核验（当为电子标且指定了交易平台时）
        List<QualityEvidence> caEvidences = kbEvidences != null
                ? kbEvidences.stream().filter(e -> KnowledgeMatch.SOURCE_CA_CERTIFICATE.equals(e.knowledgeSource())).toList()
                : List.of();

        if (requirement != null && requirement.platformName() != null && !requirement.platformName().isBlank()) {
            String platform = requirement.platformName();
            QualityEvidence matchedCa = caEvidences.stream()
                    .filter(e -> Boolean.TRUE.equals(e.matched()))
                    .findFirst()
                    .orElse(null);

            if (matchedCa == null) {
                return QualityCheckItemResult.fail(
                        item,
                        "未在 CA 证书管理中检索到匹配【" + platform + "】平台的有效 CA 证书。电子标须持有该交易平台 CA 锁完成签名、加密与解密。",
                        "请前往【资源中心 - CA证书管理】录入并配置【" + platform + "】平台的 CA 数字证书。",
                        caEvidences);
            }

            String quote = matchedCa.quote() != null ? matchedCa.quote() : "";
            if (quote.contains("已过期") || quote.contains("EXPIRED")) {
                return QualityCheckItemResult.fail(
                        item,
                        "已匹配【" + platform + "】CA 证书，但证书已过期，无法用于电子标加密及开标解密！",
                        "请前往【资源中心 - CA证书管理】联系保管人安排证书续期办理。",
                        List.of(matchedCa));
            }

            if (quote.contains("已借出") || quote.contains("已逾期") || quote.contains("BORROWED") || quote.contains("OVERDUE")) {
                return QualityCheckItemResult.manualReview(
                        item,
                        "已匹配【" + platform + "】有效 CA 证书，但实体锁当前已借出，请复核开标前能否及时归还。",
                        "请在【资源中心 - CA证书管理】中核查借用进度并联系保管人及时催还。",
                        List.of(matchedCa));
            }

            DocumentContent.PageContent certPage = findValidCertPage(bidDoc);
            if (certPage != null) {
                List<QualityEvidence> evidences = List.of(
                        QualityEvidence.bid("CA证书有效凭证页", certPage.pageNumber(),
                                BasicInfoCheckPolicy.snippet(certPage.text(), 250)),
                        matchedCa);
                return QualityCheckItemResult.pass(
                        item,
                        "已匹配【" + platform + "】有效 CA 证书（在库），且投标文件正文已附有效性证明，要素齐全。",
                        evidences);
            }

            if (requirement.requireCertificateCopy()) {
                return QualityCheckItemResult.manualReview(
                        item,
                        "已匹配【" + platform + "】有效 CA 证书（在库），但招标文件要求附证书复印件/有效性证明，标书正文未检索到明确截图。",
                        "如招标文件强制要求附图，请在标书中补充 CA 证书有效性证明截图；如仅需平台插锁签章，可人工确认通过。",
                        List.of(matchedCa));
            }

            return QualityCheckItemResult.pass(
                    item,
                    "已匹配【" + platform + "】有效 CA 证书（在库），标书正文无需单独附图，请在交易客户端完成插锁签章与加密打包。",
                    List.of(matchedCa));
        }

        // 4. 未指定具体平台但要求电子标，或通用核验
        DocumentContent.PageContent certPage = findValidCertPage(bidDoc);
        if (certPage != null) {
            List<QualityEvidence> evidences = new java.util.ArrayList<>();
            evidences.add(QualityEvidence.bid("CA证书有效凭证页", certPage.pageNumber(),
                    BasicInfoCheckPolicy.snippet(certPage.text(), 250)));
            evidences.addAll(caEvidences);
            return QualityCheckItemResult.pass(
                    item,
                    "包含真实有效的 CA 数字证书或电子签章有效性记录，要素齐全",
                    evidences);
        }

        // 5. 仅在目录中提及
        boolean mentionedInCatalog = bidDoc.pages().stream()
                .anyMatch(p -> BidLetterCheckPolicy.isCatalogOrOutlinePage(p.text()) && containsAny(p.text(), CA_HEADERS));
        if (mentionedInCatalog) {
            return QualityCheckItemResult.fail(
                    item,
                    "仅在目录中检索到 CA 数字证书条目，正文中未检测到实际证书复印件或有效性证明！",
                    "请在投标文件中补充正式的 CA 数字证书有效性证明正文",
                    caEvidences);
        }

        if (!caEvidences.isEmpty()) {
            return QualityCheckItemResult.manualReview(
                    item,
                    "标书正文未识别到 CA 证书明文字符，系统已检索到在库 CA 证书，请确认所持 CA 锁与本项目交易系统匹配。",
                    "请前往【资源中心 - CA证书管理】核对本项目交易平台对应的 CA 证书并在客户端完成签章加密。",
                    caEvidences);
        }

        return QualityCheckItemResult.manualReview(
                item,
                "未识别到 CA 证书明文字符或有效性截图（如为电子标，请在交易平台客户端核验 CA 锁签名有效性）",
                "建议前往【资源中心 - CA证书管理】校验数字证书有效性",
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
