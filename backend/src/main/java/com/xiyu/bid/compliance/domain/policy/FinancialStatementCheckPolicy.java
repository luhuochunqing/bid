// Input: 投标文件分页内容 DocumentContent + 招标财务要求 FinancialRequirement
// Output: 财务状况与财务审计报告核查结果（防目录点线假通过、防未填模板占位符、财务三表核验与年份覆盖度校验）
// Pos: compliance/domain/policy/ - 标书端财务状况与审计报告核验策略（FP-Java，无框架依赖，行预算严格 <= 300）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.compliance.domain.policy;

import com.xiyu.bid.compliance.domain.BidQualityCheckItem;
import com.xiyu.bid.compliance.domain.DocumentContent;
import com.xiyu.bid.compliance.domain.QualityCheckItemResult;
import com.xiyu.bid.compliance.domain.QualityEvidence;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 标书端财务状况证明与财务审计报告核查策略.
 */
public final class FinancialStatementCheckPolicy {

    private static final Pattern UNFILLED_PLACEHOLDER_PATTERN = Pattern.compile(
            "【此处(?:粘贴|插入|附).*?(?:财务|审计|报表).*?】|【财务.*?附后】|______年度|（格式范本）|（参考模板）"
    );

    private static final Pattern YEAR_PATTERN = Pattern.compile("20[12][0-9]年?");

    private FinancialStatementCheckPolicy() {
    }

    // REUSE-OK: * — 质检财务状况核查入口，业务域独立
    public static QualityCheckItemResult check(final DocumentContent bidDoc) {
        return check(bidDoc, FinancialRequirement.standard());
    }

    // REUSE-OK: * — 质检财务状况带契约核查入口，业务域独立
    public static QualityCheckItemResult check(
            final DocumentContent bidDoc, final FinancialRequirement requirement) {
        BidQualityCheckItem item = BidQualityCheckItem.QUALIFICATION_REVIEW;
        FinancialRequirement req = requirement == null ? FinancialRequirement.standard() : requirement;

        // 1. 招标明确免除/不涉及财务状况
        if (Boolean.FALSE.equals(req.required())) {
            return QualityCheckItemResult.pass(item, "招标文件未要求提供财务状况证明或财务审计报告（免除/不涉及）", Collections.emptyList());
        }

        if (bidDoc == null || bidDoc.pages() == null || bidDoc.pages().isEmpty()) {
            return QualityCheckItemResult.fail(item, "未在投标文件中检索到财务状况证明、财务报表或审计报告",
                    "请补充经会计师事务所审计的财务审计报告及资产负债表、利润表、现金流量表", Collections.emptyList());
        }

        List<QualityEvidence> evidences = new ArrayList<>();
        List<QualityEvidence> placeholderEvidences = new ArrayList<>();
        LinkedHashSet<String> yearsFound = new LinkedHashSet<>();

        DocumentContent.PageContent balanceSheetPage = null;
        DocumentContent.PageContent profitPage = null;
        DocumentContent.PageContent cashFlowPage = null;
        DocumentContent.PageContent auditReportPage = null;

        for (DocumentContent.PageContent page : bidDoc.pages()) {
            if (page == null || page.text() == null || page.text().isBlank()) {
                continue;
            }
            String text = page.text();

            // 过滤目录页
            if (isCatalogOrOutlinePage(text)) {
                continue;
            }

            // 探测未填模板占位符
            Matcher placeholderMatcher = UNFILLED_PLACEHOLDER_PATTERN.matcher(text);
            if (placeholderMatcher.find()) {
                placeholderEvidences.add(QualityEvidence.bid("财务报告未填占位符", page.pageNumber(),
                        BasicInfoCheckPolicy.snippetAround(text, 150, placeholderMatcher.group())));
                continue;
            }

            boolean isFinancialPage = false;

            // 审计报告正文要素
            if (auditReportPage == null && isSubstantiveAuditReport(text)) {
                auditReportPage = page;
                isFinancialPage = true;
            }

            // 资产负债表要素
            if (balanceSheetPage == null && isBalanceSheet(text)) {
                balanceSheetPage = page;
                isFinancialPage = true;
            }

            // 利润表 / 损益表要素
            if (profitPage == null && isProfitStatement(text)) {
                profitPage = page;
                isFinancialPage = true;
            }

            // 现金流量表要素
            if (cashFlowPage == null && isCashFlowStatement(text)) {
                cashFlowPage = page;
                isFinancialPage = true;
            }

            if (isFinancialPage) {
                Matcher ym = YEAR_PATTERN.matcher(text);
                while (ym.find()) {
                    yearsFound.add(ym.group().replace("年", ""));
                }
            }
        }

        // 收集有效证据链
        if (auditReportPage != null) {
            evidences.add(QualityEvidence.bid("财务审计报告页", auditReportPage.pageNumber(),
                    BasicInfoCheckPolicy.snippetAround(auditReportPage.text(), 200, "审计报告", "会计师事务所", "无保留意见", "注册会计师")));
        }
        if (balanceSheetPage != null) {
            evidences.add(QualityEvidence.bid("资产负债表所在页", balanceSheetPage.pageNumber(),
                    BasicInfoCheckPolicy.snippetAround(balanceSheetPage.text(), 200, "资产负债表", "资产总计", "所有者权益")));
        }
        if (profitPage != null) {
            evidences.add(QualityEvidence.bid("利润表所在页", profitPage.pageNumber(),
                    BasicInfoCheckPolicy.snippetAround(profitPage.text(), 200, "利润表", "损益表", "营业收入", "净利润")));
        }
        if (cashFlowPage != null) {
            evidences.add(QualityEvidence.bid("现金流量表所在页", cashFlowPage.pageNumber(),
                    BasicInfoCheckPolicy.snippetAround(cashFlowPage.text(), 200, "现金流量表", "经营活动产生的现金流量")));
        }

        // 2. 若存在未填占位符或全无实际报表
        if (!placeholderEvidences.isEmpty()) {
            return QualityCheckItemResult.fail(item, "检测到财务审计报告占位模板但未填写实际报表内容（存在留白或占位符）",
                    "请在标书中替换占位模板并附上真实经审计的财务会计报告", placeholderEvidences);
        }
        if (balanceSheetPage == null && profitPage == null && cashFlowPage == null && auditReportPage == null) {
            return QualityCheckItemResult.fail(item, "未在投标文件中检索到财务状况证明、财务报表或审计报告",
                    "请补充经会计师事务所审计的财务审计报告及资产负债表、利润表、现金流量表", Collections.emptyList());
        }

        // 3. 强事实直通 PASS：具备资产负债表且具备利润表/现金流量表
        boolean hasAudit = auditReportPage != null;
        boolean hasFullThreeStatements = balanceSheetPage != null && profitPage != null && cashFlowPage != null;
        boolean hasCoreTwoStatements = balanceSheetPage != null && (profitPage != null || cashFlowPage != null);

        // 年份覆盖度判定
        boolean yearMatches = true;
        if (!req.requiredYears().isEmpty() && !yearsFound.isEmpty()) {
            boolean anyMatch = false;
            for (String ry : req.requiredYears()) {
                if (yearsFound.contains(ry) || ry.contains("近") || ry.contains("前")) {
                    anyMatch = true;
                    break;
                }
            }
            yearMatches = anyMatch;
        }

        if (hasFullThreeStatements && (hasAudit || !Boolean.TRUE.equals(req.auditReportRequired()))) {
            if (yearMatches) {
                String yearNote = yearsFound.isEmpty() ? "" : "，报表年份涵盖 " + String.join("、", yearsFound);
                return QualityCheckItemResult.pass(item,
                        "已提供完整的财务会计报告（包含资产负债表、利润表、现金流量表"
                                + (hasAudit ? "及会计师事务所审计意见与印章" : "") + yearNote + "）",
                        evidences);
            }
        }

        // 4. 部分报表或缺失审计印章 -> 待人工复核 (MANUAL)
        List<String> missingItems = new ArrayList<>();
        if (balanceSheetPage == null) missingItems.add("资产负债表");
        if (profitPage == null) missingItems.add("利润表");
        if (cashFlowPage == null) missingItems.add("现金流量表");
        if (Boolean.TRUE.equals(req.auditReportRequired()) && !hasAudit) {
            missingItems.add("会计师事务所审计报告及印章");
        }

        String warning = missingItems.isEmpty() ? "报表年份覆盖度存疑" : "未见完整的 " + String.join("、", missingItems);
        return QualityCheckItemResult.manualReview(item,
                "已附部分财务报表（" + warning + "），建议人工核验完整性",
                "请人工核对投标文件是否包含招标文件要求年份的全部三张财务报表及审计机构印章",
                evidences);
    }

    private static boolean isCatalogOrOutlinePage(final String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        boolean hasDotLine = text.contains("......") || text.contains("……") || text.contains("······");
        boolean hasCatalogWord = text.contains("目 录") || text.contains("目录") || text.contains("TOC");
        if (hasDotLine || hasCatalogWord) {
            boolean hasSubstantiveTable = (text.contains("资产总计") || text.contains("负债合计"))
                    && (text.contains("流动资产") || text.contains("流动负债"));
            boolean hasSubstantiveOpinion = text.contains("无保留意见") || text.contains("审计意见")
                    || text.contains("我们审计了");
            return !hasSubstantiveTable && !hasSubstantiveOpinion;
        }
        return false;
    }

    private static boolean isSubstantiveAuditReport(final String text) {
        boolean hasTitle = text.contains("审计报告") || text.contains("财务审计") || text.contains("审计机构报告");
        boolean hasFirm = text.contains("会计师事务所") || text.contains("注册会计师") || text.contains("审计机构");
        boolean hasOpinion = text.contains("审计意见") || text.contains("无保留意见") || text.contains("保留意见")
                || text.contains("公允反映") || text.contains("我们审计了");
        return hasTitle && (hasFirm || hasOpinion);
    }

    private static boolean isBalanceSheet(final String text) {
        boolean hasTitle = text.contains("资产负债表");
        boolean hasElements = (text.contains("流动资产") || text.contains("非流动资产") || text.contains("资产总计"))
                && (text.contains("流动负债") || text.contains("负债合计") || text.contains("所有者权益"));
        return hasTitle || hasElements;
    }

    private static boolean isProfitStatement(final String text) {
        boolean hasTitle = text.contains("利润表") || text.contains("损益表");
        boolean hasElements = text.contains("营业收入") && (text.contains("营业利润") || text.contains("净利润") || text.contains("营业成本"));
        return hasTitle || hasElements;
    }

    private static boolean isCashFlowStatement(final String text) {
        boolean hasTitle = text.contains("现金流量表");
        boolean hasElements = text.contains("经营活动产生的现金流量")
                || text.contains("投资活动产生的现金流量")
                || text.contains("现金及现金等价物净增加额");
        return hasTitle || hasElements;
    }
}
