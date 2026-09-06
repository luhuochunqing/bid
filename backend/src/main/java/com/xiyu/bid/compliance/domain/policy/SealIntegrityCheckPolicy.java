// Input: 投标文件 DocumentContent
// Output: 签章完备性核验结果（法定代表人印章、公章、电子签章防伪及骑缝章核验）
// Pos: compliance/domain/policy/ - 签章完备性核验策略（FP-Java 纯核心，严格 <= 300 行）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.compliance.domain.policy;

import com.xiyu.bid.compliance.domain.BidQualityCheckItem;
import com.xiyu.bid.compliance.domain.DocumentContent;
import com.xiyu.bid.compliance.domain.QualityCheckItemResult;
import com.xiyu.bid.compliance.domain.QualityEvidence;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 签章完备性与防伪核验策略 (FP-Java 纯核心).
 */
public final class SealIntegrityCheckPolicy {

    private static final Pattern LEGAL_REP_PATTERN = Pattern.compile(
            "法定代表人(?:或其委托代理人)?[：:\\s]*[（\\(]?(?:签字|盖章|签字或盖章|印章|印鉴)[）\\)]?"
                    + "|法定代表人[：:\\s]+[\\u4e00-\\u9fa5]{2,4}"
                    + "|(?:签字人|授权代表|受托人)[：:\\s]+[\\u4e00-\\u9fa5]{2,4}"
    );

    private static final Pattern COMPANY_SEAL_PATTERN = Pattern.compile(
            "投标人[：:\\s]*[（\\(]?(?:盖章|盖单位公章|盖公章|加盖公章|企业电子章)[）\\)]?"
                    + "|加盖单位公章|加盖公章|（公章）|\\(公章\\)|（盖章）|\\(盖章\\)|盖章处"
                    + "|单位公章|投标单位（盖章）"
    );

    private static final Pattern E_SEAL_PATTERN = Pattern.compile(
            "电子印章|电子签章|数字签名|数字证书防伪|国密(?:SM2|SM3)签名"
                    + "|电子签名验证(?:通过|有效|成功)|电子签章防伪码|验签有效"
    );

    private static final Pattern CROSS_PAGE_SEAL_PATTERN = Pattern.compile(
            "骑缝章|加盖骑缝章|每页加盖(?:公章|骑缝章)|骑缝印章|加盖骑缝印鉴"
    );

    private static final Pattern UNFILLED_SEAL_PLACEHOLDER = Pattern.compile(
            "法定代表人[：:\\s]*_{3,}|投标人[：:\\s]*_{3,}|【此处(?:盖章|盖公章|签字)】"
                    + "|【(?:法定代表人签字|单位公章)】|（待签字）|（待盖章）"
    );

    private SealIntegrityCheckPolicy() {
    }

    public record SealEvidenceSummary(
            boolean hasLegalRepSign,
            boolean hasCompanySeal,
            boolean hasElectronicSeal,
            boolean hasCrossPageSeal,
            boolean hasUnfilledPlaceholder,
            List<QualityEvidence> evidences) {
    }

    // REUSE-OK: * — 质检签章完备性核查入口策略，业务域独立
    public static QualityCheckItemResult check(final DocumentContent bidDoc) {
        BidQualityCheckItem item = BidQualityCheckItem.SIGNATURE_SEAL;
        if (bidDoc == null || bidDoc.pages() == null || bidDoc.pages().isEmpty()) {
            return QualityCheckItemResult.fail(item, "投标文件为空，无法进行签章完整性核验", "请上传有效投标文件正文", Collections.emptyList());
        }

        SealEvidenceSummary summary = inspectSeals(bidDoc);

        // 1. 模板留白反假拦截：仅有占位符且无真实签署证据
        if (summary.hasUnfilledPlaceholder() && !summary.hasLegalRepSign() && !summary.hasCompanySeal() && !summary.hasElectronicSeal()) {
            return QualityCheckItemResult.fail(
                    item,
                    "[UNFILLED_SEAL_PLACEHOLDER] 检测到签署落款处保留未填占位符（如下划线或【待盖章】），未实际盖章或签字",
                    "请加盖单位公章及法定代表人签字/印鉴，勿保留未替换模板下划线",
                    summary.evidences());
        }

        // 2. 强凭证直通：具备电子签章防伪标记，或同时具备法定代表人签署与企业公章
        if (summary.hasElectronicSeal()) {
            String crossNote = summary.hasCrossPageSeal() ? "（含骑缝章连续性覆盖）" : "";
            return QualityCheckItemResult.pass(
                    item,
                    "已识别到合法有效的电子签章防伪验签标记" + crossNote + "，签章完备",
                    summary.evidences());
        }

        if (summary.hasLegalRepSign() && summary.hasCompanySeal()) {
            String crossNote = summary.hasCrossPageSeal() ? "，并包含每页骑缝章覆盖标记" : "";
            return QualityCheckItemResult.pass(
                    item,
                    "已检测到法定代表人印章/签字与投标人单位公章" + crossNote + "，符合签章规范",
                    summary.evidences());
        }

        // 3. 部分签章缺失场景
        if (summary.hasCompanySeal() && !summary.hasLegalRepSign()) {
            return QualityCheckItemResult.manualReview(
                    item,
                    "已检测到单位公章落款，但未能明确识别法定代表人个人印章或签字",
                    "请核对投标函与授权书是否已签署法定代表人个人签字或印章",
                    summary.evidences());
        }

        if (summary.hasLegalRepSign() && !summary.hasCompanySeal()) {
            return QualityCheckItemResult.manualReview(
                    item,
                    "已检测到法定代表人签字或签署人，但未能识别单位公章文本标记",
                    "请确保标书已加盖单位实体公章或电子印章",
                    summary.evidences());
        }

        // 4. 完全未检测到签署
        return QualityCheckItemResult.fail(
                item,
                "未在标书核心页中检测到法定代表人印章、公章或电子防伪签章",
                "请在投标函、授权书及承诺书中加盖公章与法定代表人签字/印鉴",
                Collections.emptyList());
    }

    private static SealEvidenceSummary inspectSeals(final DocumentContent bidDoc) {
        boolean hasLegalRep = false;
        boolean hasCompany = false;
        boolean hasESeal = false;
        boolean hasCross = false;
        boolean hasPlaceholder = false;

        List<QualityEvidence> evidences = new ArrayList<>();

        for (DocumentContent.PageContent page : bidDoc.pages()) {
            String text = page.text();
            if (BidLetterCheckPolicy.isCatalogOrOutlinePage(text)) {
                continue;
            }

            Matcher phMatcher = UNFILLED_SEAL_PLACEHOLDER.matcher(text);
            if (phMatcher.find()) {
                hasPlaceholder = true;
                evidences.add(QualityEvidence.bid("未填盖章占位", page.pageNumber(),
                        BasicInfoCheckPolicy.snippetAround(text, 150, phMatcher.group())));
            }

            Matcher esMatcher = E_SEAL_PATTERN.matcher(text);
            if (esMatcher.find()) {
                hasESeal = true;
                evidences.add(QualityEvidence.bid("电子防伪签章", page.pageNumber(),
                        BasicInfoCheckPolicy.snippetAround(text, 150, esMatcher.group())));
            }

            Matcher lrMatcher = LEGAL_REP_PATTERN.matcher(text);
            if (lrMatcher.find()) {
                hasLegalRep = true;
                evidences.add(QualityEvidence.bid("法定代表人签署", page.pageNumber(),
                        BasicInfoCheckPolicy.snippetAround(text, 150, lrMatcher.group())));
            }

            Matcher csMatcher = COMPANY_SEAL_PATTERN.matcher(text);
            if (csMatcher.find()) {
                hasCompany = true;
                evidences.add(QualityEvidence.bid("单位公章落款", page.pageNumber(),
                        BasicInfoCheckPolicy.snippetAround(text, 150, csMatcher.group())));
            }

            Matcher cpMatcher = CROSS_PAGE_SEAL_PATTERN.matcher(text);
            if (cpMatcher.find()) {
                hasCross = true;
                evidences.add(QualityEvidence.bid("骑缝章标记", page.pageNumber(),
                        BasicInfoCheckPolicy.snippetAround(text, 150, cpMatcher.group())));
            }
        }

        return new SealEvidenceSummary(hasLegalRep, hasCompany, hasESeal, hasCross, hasPlaceholder, evidences);
    }
}
