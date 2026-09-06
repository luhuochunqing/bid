// Input: 投标文件 DocumentContent + 可选招标授权要求 AuthorizationRequirement
// Output: 授权委托书核查结果 QualityCheckItemResult（PASS/FAIL/MANUAL）及证据
// Pos: compliance/domain/policy/ - 授权委托书与法定代表人证明反假核查策略 (FP-Java)
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.compliance.domain.policy;

import com.xiyu.bid.compliance.domain.BidQualityCheckItem;
import com.xiyu.bid.compliance.domain.DocumentContent;
import com.xiyu.bid.compliance.domain.QualityCheckItemResult;
import com.xiyu.bid.compliance.domain.QualityEvidence;

import java.util.Collections;
import java.util.List;
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

    private static final Pattern ID_CARD_PATTERN = Pattern.compile(
            "[1-9]\\d{5}(?:18|19|20)\\d{2}(?:0[1-9]|1[0-2])(?:0[1-9]|[12]\\d|3[01])\\d{3}[\\dXx]"
                    + "|[1-9]\\d{5}\\d{2}(?:0[1-9]|1[0-2])(?:0[1-9]|[12]\\d|3[01])\\d{3}"
                    + "|[1-9]\\d{5}[*×＊Xx]{4,8}\\d{3,4}[Xx\\d]?"
    );

    private static final Pattern AGENT_NAME_PATTERN = Pattern.compile(
            "(?:委托代理人|受托人|被授权人|授权代表)[（(]?(?:签字|盖章|姓名)?[）)]?[：:\\s]*([\\u4e00-\\u9fa5]{2,6})"
    );

    private static final Pattern PRINCIPAL_NAME_PATTERN = Pattern.compile(
            "(?:法定代表人|委托人)[（(]?(?:签字|盖章|姓名)?[）)]?[：:\\s]*([\\u4e00-\\u9fa5]{2,6})"
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
        PageMatch authMatch = findFirstNonCatalogPage(bidDoc, AUTH_LETTER_KEYWORDS);
        if (authMatch != null) {
            return evaluateAuthorizationPage(item, authMatch);
        }

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

        boolean hasPrincipal = text.contains("法定代表人") || text.contains("委托人")
                || PRINCIPAL_NAME_PATTERN.matcher(text).find();
        boolean hasAgent = text.contains("受托人") || text.contains("委托代理人")
                || text.contains("被授权人") || AGENT_NAME_PATTERN.matcher(text).find();
        boolean hasStrongScope = text.contains("授权范围") || text.contains("全权代表")
                || text.contains("授权期限") || text.contains("授权事项") || text.contains("代理权限")
                || text.contains("委托事项") || text.contains("委托权限");
        boolean hasIdCard = ID_CARD_PATTERN.matcher(text).find();

        if (hasPrincipal && hasAgent && (hasStrongScope || hasIdCard)) {
            return QualityCheckItemResult.pass(item,
                    "已提供法定代表人授权委托书，委托人与受托人授权关系清晰明确，要素齐全", evidences);
        }

        return QualityCheckItemResult.manualReview(item,
                "检测到授权委托书，但委托人或受托人身份信息与授权范围不完整",
                "请核实授权委托书中法定代表人与受托人姓名、身份证号及授权权限是否清晰明确", evidences);
    }

    private static QualityCheckItemResult evaluateLegalRepOnlyPage(
            final BidQualityCheckItem item, final PageMatch match, final AuthorizationRequirement requirement) {
        String text = match.text();
        List<QualityEvidence> evidences = List.of(QualityEvidence.bid(
                "法定代表人身份证明", match.pageNumber(),
                BasicInfoCheckPolicy.snippetAround(text, 200, LEGAL_REP_KEYWORDS)));

        boolean allowLegalRepOnly = requirement != null && Boolean.TRUE.equals(requirement.allowLegalRepresentativeOnly());
        boolean hasPrincipal = text.contains("法定代表人") || text.contains("身份证");

        if (allowLegalRepOnly && hasPrincipal) {
            return QualityCheckItemResult.pass(item,
                    "已附法定代表人身份证明，符合招标文件关于法定代表人亲自参加的合规规定（免除授权委托书）", evidences);
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
