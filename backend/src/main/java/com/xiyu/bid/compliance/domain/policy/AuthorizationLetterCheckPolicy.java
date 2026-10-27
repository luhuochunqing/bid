// Input: 投标文件 DocumentContent + 可选招标授权要求 AuthorizationRequirement
// Output: 授权委托书核查结果 QualityCheckItemResult（PASS/FAIL/MANUAL）及证据
// Pos: compliance/domain/policy/ - 授权委托书与法定代表人证明反假核查策略 (FP-Java)
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.compliance.domain.policy;

import com.xiyu.bid.compliance.domain.BidQualityCheckItem;
import com.xiyu.bid.compliance.domain.DocumentContent;
import com.xiyu.bid.compliance.domain.QualityCheckItemResult;
import com.xiyu.bid.compliance.domain.QualityEvidence;
import com.xiyu.bid.compliance.domain.QualityCheckStatus;

import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 授权委托书与法定代表人证明格式与要素纯函数核查策略 (FP-Java).
 *
 * <p>零框架依赖，严格拦截目录页点线与占位符模板，落实真实要素与招标契约比对.
 */
public final class AuthorizationLetterCheckPolicy {

    record PageMatch(int pageNumber, String text) { }

    static final String[] AUTH_LETTER_KEYWORDS = {
            "授权委托书", "法定代表人授权委托书", "法定代表人授权书", "法人授权委托书", "授 权 委 托 书"
    };

    static final String[] LEGAL_REP_KEYWORDS = {
            "法定代表人身份证明", "法定代表人证明书", "法定代表人声明"
    };

    private static final Pattern UNFILLED_PLACEHOLDER_PATTERN = Pattern.compile(
            "【(?:被授权人|受托人|委托代理人|姓名|身份证号|职务|授权期限|授权单位)[^】]*】|"
                    + "\\[(?:被授权人|受托人|委托代理人|姓名|身份证号|职务|授权期限|授权单位)[^\\]]*\\]|"
                    + "(?:被授权人|受托人|委托代理人|法定代表人)[：:\\s]*_{2,}|"
                    + "身份证(?:号|号码)?[：:\\s]*_{2,}|"
                    + "授权期限[：:\\s]*_{2,}"
    );

    private AuthorizationLetterCheckPolicy() {
    }

    // REUSE-OK: * — 质检授权委托书独立核查入口策略，业务域独立
    public static QualityCheckItemResult check(final DocumentContent bidDoc) {
        return check(bidDoc, null);
    }

    // REUSE-OK: * — 质检授权委托书独立核查入口策略，业务域独立
    public static QualityCheckItemResult check(
            final DocumentContent bidDoc, final AuthorizationRequirement requirement) {
        BidQualityCheckItem item = BidQualityCheckItem.AUTHORIZATION_LETTER;
        if (bidDoc == null || bidDoc.pages().isEmpty()) {
            return QualityCheckItemResult.fail(item, "未找到有效的投标文件正文内容", "请上传完整的投标文件后重新核查", Collections.emptyList());
        }

        // 1. 优先在非目录页检索真实授权委托书
        QualityCheckItemResult firstUncertain = null;
        boolean uncertainHasFields = false;
        QualityCheckItemResult firstPass = null;
        String actualGrant = null;
        boolean conflictingGrants = false;
        List<QualityEvidence> grantEvidences = new ArrayList<>();
        for (DocumentContent.PageContent page : bidDoc.pages()) {
            if (isCatalogOrOutlinePage(page.text())
                    || !BasicInfoCheckPolicy.containsAnyKeyword(page.text(), AUTH_LETTER_KEYWORDS)) continue;
            QualityCheckItemResult candidate = evaluateAuthorizationPage(item, new PageMatch(page.pageNumber(), page.text()));
            boolean declaresFacts = AuthorizationFieldPolicy.declaresAuthorizationFacts(page.text());
            if (declaresFacts) {
                String normalized = page.text().replaceAll("\\s+", "");
                if (actualGrant != null && !actualGrant.equals(normalized)) conflictingGrants = true;
                actualGrant = normalized;
                grantEvidences.addAll(candidate.evidences());
            }
            if (candidate.status() == QualityCheckStatus.PASS && firstPass == null) firstPass = candidate;
            if (firstUncertain == null || declaresFacts && !uncertainHasFields) {
                firstUncertain = candidate;
                uncertainHasFields = declaresFacts;
            }
        }
        if (conflictingGrants) return QualityCheckItemResult.manualReview(item,
                "存在多个内容不同的授权正文，无法确认本次适用的唯一人员、权限及期限",
                "请核对原页及授权版本；系统不默认选择其中一份通过", grantEvidences);
        if (firstPass != null) return firstPass;
        if (firstUncertain != null) return firstUncertain;

        // 2. 若未找到授权委托书，检索法定代表人身份证明
        PageMatch legalRepMatch = findFirstNonCatalogPage(bidDoc, LEGAL_REP_KEYWORDS);
        if (legalRepMatch != null) {
            return evaluateLegalRepOnlyPage(item, legalRepMatch, requirement);
        }

        // 3. 检查是否仅命中目录页
        PageMatch tocMatch = findCatalogPage(bidDoc, AUTH_LETTER_KEYWORDS);
        if (tocMatch != null) {
            List<QualityEvidence> evidences = List.of(QualityEvidence.bid(
                    "目录索引页", tocMatch.pageNumber(),
                    BasicInfoCheckPolicy.snippetAround(tocMatch.text(), 180, AUTH_LETTER_KEYWORDS)));
            return QualityCheckItemResult.manualReview(item,
                    "仅在目录索引中检索到授权委托书条目，未在正文定位到实际授权书",
                    "请核实投标文件正文中是否附有清晰完整的授权委托书扫描件", evidences);
        }

        return QualityCheckItemResult.fail(item,
                "未检测到法定代表人身份证明或授权委托书",
                "请在投标文件中补充法定代表人证明书及有效授权委托书，确保授权完备合规", Collections.emptyList());
    }

    private static QualityCheckItemResult evaluateAuthorizationPage(
            final BidQualityCheckItem item, final PageMatch match) {
        String text = match.text();
        List<QualityEvidence> evidences = List.of(QualityEvidence.bid(
                "授权委托书正文", match.pageNumber(),
                BasicInfoCheckPolicy.snippetAround(text, 200, AUTH_LETTER_KEYWORDS)));

        // 检查未填写的模板占位符
        Matcher placeholderMatcher = UNFILLED_PLACEHOLDER_PATTERN.matcher(text);
        if (placeholderMatcher.find()) {
            return QualityCheckItemResult.manualReview(item,
                    "检测到授权委托书格式，但存在未填写的模板占位符或下划线留白（" + placeholderMatcher.group() + "）",
                    "请确保授权委托书已完成真实填写、签署并加盖公章，避免留白废标风险", evidences);
        }

        AuthorizationFieldPolicy.Assessment fields = AuthorizationFieldPolicy.assessAuthorizationFields(text);
        boolean isInstructions = text.contains("填写要求") || text.contains("填表说明") || text.contains("格式说明");
        if (fields.filled() && !isInstructions) {
            return QualityCheckItemResult.pass(item,
                    "已提供法定代表人授权委托书，授权人员、权限及期限文字要素齐全（身份与签章须另行核验）", evidences);
        }
        return QualityCheckItemResult.manualReview(item,
                fields.hasPlaceholder() ? "授权字段包含占位或空白，不能确认已真实填写"
                        : "授权人员、权限或期限尚无法正向确认，不能仅凭非空文字判定完整",
                "请核对原页的授权人员、具体代理权限及明确期限；未识别写法保留人工，不推断材料缺失", evidences);
    }

    public static QualityCheckItemResult enforcePositiveContract(final QualityCheckItemResult result,
            final DocumentContent document, final AuthorizationRequirement requirement) {
        if (result == null || !BidQualityCheckItem.AUTHORIZATION_LETTER.getCode().equals(result.itemCode())) return result;
        QualityCheckItemResult deterministic = check(document, requirement);
        if (deterministic.status() == QualityCheckStatus.MANUAL_REVIEW) return ManualReasons.manual(
                BidQualityCheckItem.AUTHORIZATION_LETTER, ManualReasons.EVIDENCE_INSUFFICIENT,
                deterministic.conclusion(), deterministic.suggestion(), deterministic.evidences());
        if (result.status() != QualityCheckStatus.PASS) return result;
        if (deterministic.status() == QualityCheckStatus.PASS) return deterministic;
        return QualityCheckItemResult.manualReview(BidQualityCheckItem.AUTHORIZATION_LETTER,
                "授权人员、权限或期限缺少正向填写证据，AI通过结论不能替代字段核验",
                deterministic.suggestion(), deterministic.evidences());
    }

    private static QualityCheckItemResult evaluateLegalRepOnlyPage(
            final BidQualityCheckItem item, final PageMatch match, final AuthorizationRequirement requirement) {
        String text = match.text();
        List<QualityEvidence> evidences = List.of(QualityEvidence.bid(
                "法定代表人身份证明", match.pageNumber(),
                BasicInfoCheckPolicy.snippetAround(text, 200, LEGAL_REP_KEYWORDS)));

        boolean allowLegalRepOnly = requirement != null && Boolean.TRUE.equals(requirement.allowLegalRepresentativeOnly());
        if (allowLegalRepOnly && AuthorizationFieldPolicy.legalRepresentativeProofFilled(text)) {
            return QualityCheckItemResult.pass(item,
                    "已附法定代表人身份证明，招标允许仅附该证明（免除授权委托书）；实际参与人员身份与签章须另行核验", evidences);
        }

        return QualityCheckItemResult.manualReview(item,
                "仅检测到法定代表人身份证明，未检测到授权委托书",
                "若由法定代表人亲自签署并参加投标可免除授权书；若由委托代理人参加，请务必补充授权委托书", evidences);
    }

    static PageMatch findFirstNonCatalogPage(final DocumentContent bidDoc, final String... keywords) {
        for (DocumentContent.PageContent page : bidDoc.pages()) {
            if (isCatalogOrOutlinePage(page.text())) {
                continue;
            }
            if (BasicInfoCheckPolicy.containsAnyKeyword(page.text(), keywords)) {
                return new PageMatch(page.pageNumber(), page.text());
            }
        }
        return null;
    }

    static PageMatch findCatalogPage(final DocumentContent bidDoc, final String... keywords) {
        for (DocumentContent.PageContent page : bidDoc.pages()) {
            if (isCatalogOrOutlinePage(page.text()) && BasicInfoCheckPolicy.containsAnyKeyword(page.text(), keywords)) {
                return new PageMatch(page.pageNumber(), page.text());
            }
        }
        return null;
    }

    static boolean isCatalogOrOutlinePage(final String pageText) {
        return BidLetterCheckPolicy.isCatalogOrOutlinePage(pageText);
    }
}
