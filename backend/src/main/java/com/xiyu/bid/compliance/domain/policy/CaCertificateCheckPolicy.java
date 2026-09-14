// Input: 投标文件分页内容 (DocumentContent) + 可选 CA 证书要求契约 (CaCertificateRequirement) + 知识库匹配证据 (kbEvidences)
// Output: 电子标 CA 证书核查结果 (QualityCheckItemResult: PASS / FAIL / MANUAL_REVIEW)
// Pos: compliance/domain/policy/ - 电子标 CA 证书台账核查策略（纯核心 FP-Java，基于企业知识库真实台账核验）
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
 * 标书电子标 CA 数字证书 / CA 锁台账核查策略 (FP-Java 纯核心).
 *
 * <p>核心核验企业知识库（CA证书管理台账）中是否存在用于本项目交易平台的有效 CA 锁，
 * 证书是否在有效期内、是否在库可用（未借出），保障投标及开标解密全周期顺畅；
 * 投标文件正文中无需强制附带证书复印件或截图。
 */
public final class CaCertificateCheckPolicy {

    private static final String[] CA_HEADERS = {
            "CA数字证书", "数字证书认证", "电子签章", "电子印章", "CA证书", "数字证书", "CA电子签章", "CA认证"
    };

    private static final Pattern UNFILLED_PLACEHOLDER_PATTERN = Pattern.compile(
            "【此处(?:填写|粘贴|附|插入).*?(?:CA|数字证书|电子签章|电子印章).*?】|"
                    + "（此处(?:填写|粘贴|附|插入).*?(?:CA|数字证书|电子签章|电子印章).*?）|"
                    + "\\[此处(?:填写|粘贴|附|插入).*?(?:CA|数字证书|电子签章|电子印章).*?\\]|"
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
                    "【不涉及】招标文件未要求 CA 电子签章或数字证书（纸质标/线下投标无需使用平台 CA 锁）。",
                    Collections.emptyList());
        }

        // 2. 标书正文占位模板未填拦截（仅拦截未填占位符，如【此处粘贴CA证书】留白）
        if (bidDoc != null && !bidDoc.pages().isEmpty()) {
            DocumentContent.PageContent placeholderPage = findPlaceholderPage(bidDoc);
            if (placeholderPage != null) {
                List<QualityEvidence> evidences = List.of(QualityEvidence.bid(
                        "CA证书占位页", placeholderPage.pageNumber(),
                        BasicInfoCheckPolicy.snippet(placeholderPage.text(), 200)));
                return QualityCheckItemResult.fail(
                        item,
                        "检测到标书正文中包含未填写的 CA 证书占位模板或空白下划线，请清理未替换的模板占位符。",
                        "请在投标文件中删除或替换未填写的【此处粘贴CA证书】等模板占位符",
                        evidences);
            }
        }

        // 3. 提取知识库 CA 证书台账记录
        List<QualityEvidence> caEvidences = kbEvidences != null
                ? kbEvidences.stream().filter(e -> KnowledgeMatch.SOURCE_CA_CERTIFICATE.equals(e.knowledgeSource())).toList()
                : List.of();

        String platform = (requirement != null && requirement.platformName() != null && !requirement.platformName().isBlank())
                ? requirement.platformName()
                : null;

        // 4. 根据指定平台匹配知识库台账
        if (platform != null) {
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
                        "已在【CA证书管理】匹配到【" + platform + "】CA 证书，但证书已过期，无法用于电子标加密及开标解密！",
                        "高风险项，证书失效将导致无法开标解密。请立即前往【资源中心 - CA证书管理】联系保管人安排续期办理。",
                        List.of(matchedCa));
            }

            if (quote.contains("已借出") || quote.contains("已逾期") || quote.contains("BORROWED") || quote.contains("OVERDUE")) {
                return QualityCheckItemResult.manualReview(
                        item,
                        "已在【CA证书管理】匹配到【" + platform + "】有效 CA 证书，但实体锁当前已借出，请复核开标前能否及时归还。",
                        "请在投标截止及开标解密前，前往【资源中心 - CA证书管理】核对借用进度并联系保管人及时催还。",
                        List.of(matchedCa));
            }

            return QualityCheckItemResult.pass(
                    item,
                    "已在【CA证书管理】检索到匹配【" + platform + "】的有效 CA 证书（在库可用），满足电子投标及开标解密要求。",
                    List.of(matchedCa));
        }

        // 5. 通用电子标（未指定具体交易平台名称）
        QualityEvidence inLibraryCa = caEvidences.stream()
                .filter(e -> {
                    String q = e.quote() != null ? e.quote() : "";
                    return !q.contains("已过期") && !q.contains("EXPIRED")
                            && !q.contains("已借出") && !q.contains("已逾期")
                            && !q.contains("BORROWED") && !q.contains("OVERDUE");
                })
                .findFirst()
                .orElse(null);

        if (inLibraryCa != null) {
            return QualityCheckItemResult.pass(
                    item,
                    "本项目为电子标，已在【CA证书管理】中检索到在库可用的企业有效 CA 证书，满足电子投标用锁要求。",
                    List.of(inLibraryCa));
        }

        if (!caEvidences.isEmpty()) {
            QualityEvidence borrowedCa = caEvidences.stream().filter(e -> {
                String q = e.quote() != null ? e.quote() : "";
                return q.contains("已借出") || q.contains("BORROWED");
            }).findFirst().orElse(null);
            if (borrowedCa != null) {
                return QualityCheckItemResult.manualReview(
                        item,
                        "本项目为电子标，在【CA证书管理】中检索到 CA 证书，但当前已借出，请在开标前及时催还。",
                        "请前往【资源中心 - CA证书管理】联系保管人催还实体锁。",
                        List.of(borrowedCa));
            }
            return QualityCheckItemResult.fail(
                    item,
                    "本项目为电子标，但【CA证书管理】中现存 CA 证书均已过期，无法用于电子标解密！",
                    "高风险项，证书失效将导致无法解密。请前往【资源中心 - CA证书管理】联系保管人安排证书续期。",
                    caEvidences);
        }

        return QualityCheckItemResult.manualReview(
                item,
                "本项目为电子标，但在【CA证书管理】中未检索到企业 CA 数字证书台账记录。",
                "请前往【资源中心 - CA证书管理】确认并录入本项目交易平台对应的 CA 数字证书。",
                Collections.emptyList());
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
